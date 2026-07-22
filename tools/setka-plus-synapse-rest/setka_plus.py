# Setka Plus + Robokassa — прямой REST-патч Synapse (без modules)
#
# Файлы:
#   setka_plus.py          -> .../site-packages/synapse/rest/client/setka_plus.py
#   setka_plus.yaml        -> /etc/matrix-synapse/setka_plus.yaml
#
# Патчи Synapse (2 места, см. README.md):
#   synapse/rest/__init__.py
#   synapse/app/homeserver.py

"""
Setka Plus REST servlets for Synapse.

Matrix Client API (auth required):
  GET  /_matrix/client/v3/user/{userId}/setka_plus/subscription
  GET  /_matrix/client/v3/user/{userId}/setka_plus/plans
  POST /_matrix/client/v3/user/{userId}/setka_plus/payments/robokassa/create
  GET  /_matrix/client/v3/user/{userId}/setka_plus/payments/{paymentId}

Robokassa (public, via build_robokassa_resources):
  GET/POST /setka/robokassa/result
  GET      /setka/robokassa/success|fail|return/success|return/fail
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from http import HTTPStatus
from typing import TYPE_CHECKING, Any, Dict, List, Optional, Tuple
from urllib.parse import quote, unquote_plus, urlencode

import yaml
from twisted.web.resource import Resource
from twisted.web.server import NOT_DONE_YET

from synapse.api.errors import Codes, SynapseError
from synapse.http.server import HttpServer, finish_request, respond_with_html
from synapse.http.servlet import RestServlet, parse_json_object_from_request
from synapse.http.site import SynapseRequest
from synapse.types import JsonDict, UserID

if TYPE_CHECKING:
    from synapse.server import HomeServer

logger = logging.getLogger(__name__)

ROBOKASSA_PAY_URL = "https://auth.robokassa.ru/Merchant/Index.aspx"
DEFAULT_CONFIG_PATH = "/etc/matrix-synapse/setka_plus.yaml"

_STORE_CACHE: Dict[int, "SetkaPlusStore"] = {}
_CONFIG_CACHE: Optional["SetkaPlusConfig"] = None


# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class SetkaPlusPlanConfig:
    id: str
    name: str
    price_rub: float
    duration_days: int
    features: List[str] = field(default_factory=list)
    active: bool = True
    sort_order: int = 0


@dataclass(frozen=True)
class SetkaPlusConfig:
    robokassa_merchant_login: str
    robokassa_password1: str
    robokassa_password2: str
    robokassa_is_test: bool
    public_base_url: str
    plans: List[SetkaPlusPlanConfig]


def _config_path() -> str:
    return os.environ.get("SETKA_PLUS_CONFIG", DEFAULT_CONFIG_PATH)


def load_setka_config(hs: "HomeServer") -> SetkaPlusConfig:
    global _CONFIG_CACHE
    if _CONFIG_CACHE is not None:
        return _CONFIG_CACHE

    path = _config_path()
    if not os.path.isfile(path):
        raise RuntimeError(
            f"Setka Plus config not found: {path}. "
            f"Copy setka_plus.yaml.example to {path}"
        )

    with open(path, "r", encoding="utf-8") as handle:
        raw = yaml.safe_load(handle) or {}

    merchant = str(raw.get("robokassa_merchant_login", "")).strip()
    password1 = str(raw.get("robokassa_password1", "")).strip()
    password2 = str(raw.get("robokassa_password2", "")).strip()
    if not merchant or not password1 or not password2:
        raise RuntimeError("Setka Plus: robokassa credentials missing in setka_plus.yaml")

    public_base_url = str(raw.get("public_base_url") or "").strip().rstrip("/")
    if not public_base_url:
        public_base_url = hs.config.server.public_baseurl.rstrip("/")
    if not public_base_url.startswith("https://"):
        raise RuntimeError("Setka Plus: public_base_url must start with https://")

    plans_raw = raw.get("plans") or []
    if not isinstance(plans_raw, list) or not plans_raw:
        raise RuntimeError("Setka Plus: plans must be a non-empty list in setka_plus.yaml")

    plans: List[SetkaPlusPlanConfig] = []
    for index, item in enumerate(plans_raw):
        if not isinstance(item, dict):
            raise RuntimeError(f"Setka Plus: plans[{index}] must be a mapping")
        plan_id = str(item.get("id", "")).strip()
        name = str(item.get("name", "")).strip()
        price_rub = float(item.get("price_rub", 0))
        duration_days = int(item.get("duration_days", 0))
        if not plan_id or not name or price_rub <= 0 or duration_days <= 0:
            raise RuntimeError(f"Setka Plus: invalid plan at index {index}")
        features = item.get("features") or []
        plans.append(
            SetkaPlusPlanConfig(
                id=plan_id,
                name=name,
                price_rub=price_rub,
                duration_days=duration_days,
                features=[str(f) for f in features if str(f).strip()],
                active=bool(item.get("active", True)),
                sort_order=int(item.get("sort_order", index)),
            )
        )

    _CONFIG_CACHE = SetkaPlusConfig(
        robokassa_merchant_login=merchant,
        robokassa_password1=password1,
        robokassa_password2=password2,
        robokassa_is_test=bool(raw.get("robokassa_is_test", True)),
        public_base_url=public_base_url,
        plans=plans,
    )
    logger.info("Setka Plus config loaded from %s (%d plans)", path, len(plans))
    return _CONFIG_CACHE


def resolve_data_dir(hs: "HomeServer") -> str:
    cfg = hs.config
    if hasattr(cfg, "data_dir_path") and cfg.data_dir_path:
        return os.path.abspath(cfg.data_dir_path)
    server = cfg.server
    if hasattr(server, "data_dir") and server.data_dir:
        return os.path.abspath(server.data_dir)
    for candidate in (
        "/var/lib/matrix-synapse",
        "/var/lib/synapse",
        os.path.join(os.path.dirname(_config_path()), "data"),
    ):
        if os.path.isdir(candidate):
            return candidate
    data_dir = os.path.join(os.getcwd(), "data")
    os.makedirs(data_dir, exist_ok=True)
    return data_dir


# ---------------------------------------------------------------------------
# Store (JSON in data_dir/setka_plus/)
# ---------------------------------------------------------------------------


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class SetkaPlusStore:
    def __init__(self, hs: "HomeServer", config: SetkaPlusConfig):
        self.hs = hs
        self.config = config
        self._lock = threading.Lock()
        base = os.path.join(resolve_data_dir(hs), "setka_plus")
        self._dir = base
        self._payments_path = os.path.join(base, "payments.json")
        self._subscriptions_path = os.path.join(base, "subscriptions.json")
        self._meta_path = os.path.join(base, "meta.json")
        os.makedirs(base, exist_ok=True)
        self._ensure_files()
        logger.info("Setka Plus store at %s", base)

    def get_plan(self, plan_id: str) -> Optional[SetkaPlusPlanConfig]:
        for plan in self.config.plans:
            if plan.id == plan_id and plan.active:
                return plan
        return None

    def list_plans(self) -> List[SetkaPlusPlanConfig]:
        return sorted(
            [p for p in self.config.plans if p.active],
            key=lambda p: (p.sort_order, p.price_rub),
        )

    def create_payment(self, user_id: str, plan: SetkaPlusPlanConfig) -> Dict[str, Any]:
        with self._lock:
            data = self._read_json(self._payments_path)
            meta = self._read_json(self._meta_path)
            inv_id = int(meta.get("next_inv_id", 1))
            meta["next_inv_id"] = inv_id + 1
            payment_id = f"pay-{uuid.uuid4().hex[:16]}"
            data[payment_id] = {
                "payment_id": payment_id,
                "inv_id": inv_id,
                "user_id": user_id,
                "plan_id": plan.id,
                "amount": plan.price_rub,
                "description": plan.name,
                "status": "pending",
                "created_at": _utcnow().isoformat(),
                "paid_at": None,
                "message": None,
            }
            self._write_json(self._payments_path, data)
            self._write_json(self._meta_path, meta)
            return data[payment_id]

    def get_payment(self, payment_id: str, user_id: Optional[str] = None) -> Optional[Dict[str, Any]]:
        with self._lock:
            payment = self._read_json(self._payments_path).get(payment_id)
            if payment is None:
                return None
            if user_id is not None and payment.get("user_id") != user_id:
                return None
            return dict(payment)

    def get_payment_by_inv_id(self, inv_id: int) -> Optional[Dict[str, Any]]:
        with self._lock:
            for payment in self._read_json(self._payments_path).values():
                if int(payment.get("inv_id", -1)) == inv_id:
                    return dict(payment)
            return None

    def mark_payment_paid(self, payment_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            data = self._read_json(self._payments_path)
            payment = data.get(payment_id)
            if payment is None:
                return None
            if payment.get("status") == "paid":
                return dict(payment)
            payment["status"] = "paid"
            payment["paid_at"] = _utcnow().isoformat()
            payment["message"] = None
            data[payment_id] = payment
            self._write_json(self._payments_path, data)
            plan = self.get_plan(payment["plan_id"])
            days = plan.duration_days if plan else 30
            self._activate_subscription_locked(payment["user_id"], payment["plan_id"], days)
            return dict(payment)

    def mark_payment_failed(self, payment_id: str, message: str) -> None:
        with self._lock:
            data = self._read_json(self._payments_path)
            payment = data.get(payment_id)
            if payment is None:
                return
            payment["status"] = "failed"
            payment["message"] = message
            data[payment_id] = payment
            self._write_json(self._payments_path, data)

    def get_subscription(self, user_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            sub = self._read_json(self._subscriptions_path).get(user_id)
            if sub is None:
                return None
            expires_at = datetime.fromisoformat(sub["expires_at"])
            if sub.get("status") != "active" or expires_at <= _utcnow():
                return None
            return dict(sub)

    def subscription_active(self, user_id: str) -> bool:
        return self.get_subscription(user_id) is not None

    def _activate_subscription_locked(self, user_id: str, plan_id: str, duration_days: int) -> None:
        subs = self._read_json(self._subscriptions_path)
        now = _utcnow()
        expires = now + timedelta(days=duration_days)
        subs[user_id] = {
            "user_id": user_id,
            "plan_id": plan_id,
            "status": "active",
            "started_at": now.isoformat(),
            "expires_at": expires.isoformat(),
            "updated_at": now.isoformat(),
        }
        self._write_json(self._subscriptions_path, subs)

    def _ensure_files(self) -> None:
        if not os.path.exists(self._payments_path):
            self._write_json(self._payments_path, {})
        if not os.path.exists(self._subscriptions_path):
            self._write_json(self._subscriptions_path, {})
        if not os.path.exists(self._meta_path):
            self._write_json(self._meta_path, {"next_inv_id": 1})

    @staticmethod
    def _read_json(path: str) -> Dict[str, Any]:
        if not os.path.exists(path):
            return {}
        with open(path, "r", encoding="utf-8") as handle:
            content = handle.read().strip()
            if not content:
                return {}
            data = json.loads(content)
            return data if isinstance(data, dict) else {}

    @staticmethod
    def _write_json(path: str, data: Dict[str, Any]) -> None:
        temp = f"{path}.tmp"
        with open(temp, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
        os.replace(temp, path)


def get_store(hs: "HomeServer") -> SetkaPlusStore:
    key = id(hs)
    if key not in _STORE_CACHE:
        _STORE_CACHE[key] = SetkaPlusStore(hs, load_setka_config(hs))
    return _STORE_CACHE[key]


# ---------------------------------------------------------------------------
# Robokassa
# ---------------------------------------------------------------------------


def _md5_hex(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()


def _sorted_shp(params: Dict[str, str]) -> List[Tuple[str, str]]:
    shp = [(k, v) for k, v in params.items() if k.lower().startswith("shp_")]
    return sorted(shp, key=lambda item: item[0].lower())


def _signature_base(*parts: str, shp: Optional[Dict[str, str]] = None) -> str:
    base = list(parts)
    if shp:
        for key, value in _sorted_shp(shp):
            base.append(f"{key}={value}")
    return ":".join(base)


def verify_result_signature(
    out_sum: str, inv_id: str, signature: str, password2: str, shp: Dict[str, str]
) -> bool:
    base = _signature_base(out_sum, inv_id, password2, shp=shp)
    return _md5_hex(base).upper() == signature.upper()


def verify_success_signature(
    out_sum: str, inv_id: str, signature: str, password1: str, shp: Dict[str, str]
) -> bool:
    base = _signature_base(out_sum, inv_id, password1, shp=shp)
    return _md5_hex(base).upper() == signature.upper()


def build_checkout_url(
    cfg: SetkaPlusConfig,
    inv_id: int,
    amount: float,
    description: str,
    payment_id: str,
    user_id: str,
) -> str:
    out_sum = f"{amount:.2f}"
    shp = {"Shp_payment_id": payment_id, "Shp_user_id": user_id}
    sig = _md5_hex(
        _signature_base(cfg.robokassa_merchant_login, out_sum, str(inv_id), cfg.robokassa_password1, shp=shp)
    )
    query = {
        "MerchantLogin": cfg.robokassa_merchant_login,
        "OutSum": out_sum,
        "InvId": str(inv_id),
        "Description": description,
        "SignatureValue": sig,
        "Shp_payment_id": payment_id,
        "Shp_user_id": user_id,
        "SuccessURL": f"{cfg.public_base_url}/setka/robokassa/success",
        "FailURL": f"{cfg.public_base_url}/setka/robokassa/fail",
        "ResultURL": f"{cfg.public_base_url}/setka/robokassa/result",
        "IsTest": "1" if cfg.robokassa_is_test else "0",
    }
    return f"{ROBOKASSA_PAY_URL}?{urlencode(query)}"


def _app_redirect(cfg: SetkaPlusConfig, user_id: str, payment_id: str, success: bool) -> str:
    segment = "success" if success else "fail"
    return f"{cfg.public_base_url}/setka/robokassa/return/{segment}?{urlencode({'user_id': user_id, 'payment_id': payment_id})}"


def _elementx_redirect(user_id: str, payment_id: str, success: bool) -> str:
    segment = "success" if success else "fail"
    return f"elementx://open/{quote(user_id, safe='')}/setka-plus/payment/{segment}?{urlencode({'payment_id': payment_id})}"


def _ms(iso: str) -> int:
    return int(datetime.fromisoformat(iso).timestamp() * 1000)


async def _assert_self(request: SynapseRequest, auth, user_id: str) -> None:
    if not UserID.is_valid(user_id):
        raise SynapseError(HTTPStatus.BAD_REQUEST, "Invalid user id", Codes.INVALID_PARAM)
    requester = await auth.get_user_by_req(request)
    if requester.user.to_string() != user_id:
        raise SynapseError(HTTPStatus.FORBIDDEN, "Can only access your own Setka Plus data")


# ---------------------------------------------------------------------------
# Matrix Client REST servlets
# ---------------------------------------------------------------------------


class SetkaPlusSubscriptionServlet(RestServlet):
    PATTERNS = [re.compile(r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/subscription$")]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.hs = hs
        self.auth = hs.get_auth()

    async def on_GET(self, request: SynapseRequest, user_id: str) -> Tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        store = get_store(self.hs)
        sub = store.get_subscription(user_id)
        if sub is None:
            return 200, {
                "tier": "setka_plus",
                "plan_name": None,
                "status": "inactive",
                "is_active": False,
            }
        plan = store.get_plan(sub["plan_id"])
        return 200, {
            "tier": sub["plan_id"],
            "plan_name": plan.name if plan else sub["plan_id"],
            "status": "active",
            "is_active": True,
            "started_at": _ms(sub["started_at"]),
            "expires_at": _ms(sub["expires_at"]),
            "updated_at": _ms(sub["updated_at"]),
        }


class SetkaPlusPlansServlet(RestServlet):
    PATTERNS = [re.compile(r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/plans$")]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.hs = hs
        self.auth = hs.get_auth()

    async def on_GET(self, request: SynapseRequest, user_id: str) -> Tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        store = get_store(self.hs)
        plans = [
            {
                "id": p.id,
                "name": p.name,
                "price_rub": p.price_rub,
                "duration_days": p.duration_days,
                "features": p.features,
                "active": p.active,
                "sort_order": p.sort_order,
            }
            for p in store.list_plans()
        ]
        return 200, {"plans": plans}


class SetkaPlusCreatePaymentServlet(RestServlet):
    PATTERNS = [
        re.compile(
            r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/payments/robokassa/create$"
        )
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.hs = hs
        self.auth = hs.get_auth()

    async def on_POST(self, request: SynapseRequest, user_id: str) -> Tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        store = get_store(self.hs)
        cfg = store.config
        body = parse_json_object_from_request(request)
        plan_id = str(body.get("plan_id", "")).strip()
        plan = store.get_plan(plan_id)
        if plan is None:
            raise SynapseError(HTTPStatus.NOT_FOUND, "Plan not found", Codes.NOT_FOUND)
        payment = store.create_payment(user_id, plan)
        checkout_url = build_checkout_url(
            cfg,
            int(payment["inv_id"]),
            float(plan.price_rub),
            plan.name,
            payment["payment_id"],
            user_id,
        )
        return 200, {"payment_id": payment["payment_id"], "checkout_url": checkout_url}


class SetkaPlusPaymentStatusServlet(RestServlet):
    PATTERNS = [
        re.compile(
            r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/payments/(?P<payment_id>[^/]+)$"
        )
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.hs = hs
        self.auth = hs.get_auth()

    async def on_GET(
        self, request: SynapseRequest, user_id: str, payment_id: str
    ) -> Tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        store = get_store(self.hs)
        payment = store.get_payment(payment_id, user_id=user_id)
        if payment is None:
            raise SynapseError(HTTPStatus.NOT_FOUND, "Payment not found", Codes.NOT_FOUND)
        return 200, {
            "payment_id": payment["payment_id"],
            "status": payment["status"],
            "plan_id": payment["plan_id"],
            "subscription_active": store.subscription_active(user_id),
            "message": payment.get("message"),
        }


def register_servlets(hs: "HomeServer", http_server: HttpServer) -> None:
    SetkaPlusSubscriptionServlet(hs).register(http_server)
    SetkaPlusPlansServlet(hs).register(http_server)
    SetkaPlusCreatePaymentServlet(hs).register(http_server)
    SetkaPlusPaymentStatusServlet(hs).register(http_server)
    logger.info("Setka Plus Matrix REST servlets registered")


# ---------------------------------------------------------------------------
# Robokassa public resources (mounted in homeserver.py)
# ---------------------------------------------------------------------------


def _plain_text(request: SynapseRequest, code: int, body: str) -> None:
    payload = body.encode("utf-8")
    request.setResponseCode(code)
    if request._disconnected:
        return
    request.setHeader(b"Content-Type", b"text/plain; charset=utf-8")
    request.setHeader(b"Content-Length", b"%d" % (len(payload),))
    request.write(payload)
    finish_request(request)


def _extract_params(request: SynapseRequest) -> Dict[str, str]:
    params: Dict[str, str] = {}
    if request.args:
        for key, values in request.args.items():
            if values:
                params[key.decode("utf-8")] = values[0].decode("utf-8")
    return params


class _RobokassaResultResource(Resource):
    isLeaf = True

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.hs = hs

    def render_GET(self, request: SynapseRequest) -> bytes:
        self._handle(request)
        return NOT_DONE_YET

    def render_POST(self, request: SynapseRequest) -> bytes:
        content = request.content.read().decode("utf-8")
        if content:
            for pair in content.split("&"):
                if "=" not in pair:
                    continue
                key, value = pair.split("=", 1)
                request.args.setdefault(key.encode("utf-8"), []).append(
                    unquote_plus(value).encode("utf-8")
                )
        self._handle(request)
        return NOT_DONE_YET

    def _handle(self, request: SynapseRequest) -> None:
        store = get_store(self.hs)
        cfg = store.config
        params = _extract_params(request)
        out_sum = params.get("OutSum", "")
        inv_id = params.get("InvId", "")
        signature = params.get("SignatureValue", "")
        shp = {k: v for k, v in params.items() if k.lower().startswith("shp_")}

        if not out_sum or not inv_id or not signature:
            _plain_text(request, 400, "Missing params")
            return
        if not verify_result_signature(out_sum, inv_id, signature, cfg.robokassa_password2, shp):
            _plain_text(request, 400, "Bad signature")
            return

        payment = store.get_payment_by_inv_id(int(inv_id))
        if payment is None:
            _plain_text(request, 404, "Payment not found")
            return

        store.mark_payment_paid(payment["payment_id"])
        _plain_text(request, 200, f"OK{inv_id}")


class _RobokassaRedirectResource(Resource):
    isLeaf = True

    def __init__(self, hs: "HomeServer", success: bool):
        super().__init__()
        self.hs = hs
        self.success = success

    def render_GET(self, request: SynapseRequest) -> bytes:
        store = get_store(self.hs)
        cfg = store.config
        params = _extract_params(request)
        out_sum = params.get("OutSum", "")
        inv_id = params.get("InvId", "")
        signature = params.get("SignatureValue", "")
        shp = {k: v for k, v in params.items() if k.lower().startswith("shp_")}
        payment_id = shp.get("Shp_payment_id", "")
        user_id = shp.get("Shp_user_id", "")

        if self.success and out_sum and inv_id and signature:
            if not verify_success_signature(out_sum, inv_id, signature, cfg.robokassa_password1, shp):
                request.redirect(_app_redirect(cfg, user_id, payment_id, False).encode("utf-8"))
                request.finish()
                return b""

        if inv_id and (not payment_id or not user_id):
            payment = store.get_payment_by_inv_id(int(inv_id))
            if payment:
                payment_id = payment["payment_id"]
                user_id = payment["user_id"]

        if not self.success and payment_id:
            store.mark_payment_failed(payment_id, "Оплата отменена")

        request.redirect(_app_redirect(cfg, user_id, payment_id, self.success).encode("utf-8"))
        request.finish()
        return b""


class _SetkaReturnResource(Resource):
    isLeaf = True

    def __init__(self, success: bool):
        super().__init__()
        self.success = success

    def render_GET(self, request: SynapseRequest) -> bytes:
        params = _extract_params(request)
        deeplink = _elementx_redirect(
            params.get("user_id", ""), params.get("payment_id", ""), self.success
        )
        title = "Оплата прошла" if self.success else "Оплата не прошла"
        body = "Возвращаем в Setka…" if self.success else "Оплата не завершена."
        html = f"""<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8"><title>{title}</title>
<meta http-equiv="refresh" content="0;url={deeplink}"></head>
<body><p>{body}</p><p><a href="{deeplink}">Открыть приложение</a></p></body></html>"""
        respond_with_html(request, 200, html)
        return b""


def build_robokassa_resources(hs: "HomeServer") -> Dict[str, Resource]:
    """Call from synapse/app/homeserver.py before resources.update(_module_web_resources)."""
    load_setka_config(hs)
    return {
        "/setka/robokassa/result": _RobokassaResultResource(hs),
        "/setka/robokassa/success": _RobokassaRedirectResource(hs, success=True),
        "/setka/robokassa/fail": _RobokassaRedirectResource(hs, success=False),
        "/setka/robokassa/return/success": _SetkaReturnResource(success=True),
        "/setka/robokassa/return/fail": _SetkaReturnResource(success=False),
    }

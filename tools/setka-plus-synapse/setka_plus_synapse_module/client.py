from __future__ import annotations

import re
from http import HTTPStatus
from typing import TYPE_CHECKING, Any, Dict

from synapse.api.errors import Codes, SynapseError
from synapse.http.server import HttpServer
from synapse.http.servlet import RestServlet, parse_json_object_from_request
from synapse.http.site import SynapseRequest
from synapse.types import JsonDict, UserID

from .runtime import get_module_instance
from .robokassa import build_checkout_url

if TYPE_CHECKING:
    from synapse.server import HomeServer


def _ms(dt_iso: str) -> int:
    from datetime import datetime

    return int(datetime.fromisoformat(dt_iso).timestamp() * 1000)


class SetkaPlusSubscriptionServlet(RestServlet):
    PATTERNS = [
        re.compile(r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/subscription$"),
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.auth = hs.get_auth()

    async def on_GET(self, request: SynapseRequest, user_id: str) -> tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        module = get_module_instance()
        sub = module.store.get_subscription(user_id)
        if sub is None:
            return 200, {
                "tier": "setka_plus",
                "plan_name": None,
                "status": "inactive",
                "is_active": False,
            }
        plan = module.store.get_plan(sub["plan_id"])
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
    PATTERNS = [
        re.compile(r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/plans$"),
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.auth = hs.get_auth()

    async def on_GET(self, request: SynapseRequest, user_id: str) -> tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        module = get_module_instance()
        plans = [
            {
                "id": plan.id,
                "name": plan.name,
                "price_rub": plan.price_rub,
                "duration_days": plan.duration_days,
                "features": plan.features,
                "active": plan.active,
                "sort_order": plan.sort_order,
            }
            for plan in module.store.list_plans()
        ]
        return 200, {"plans": plans}


class SetkaPlusCreatePaymentServlet(RestServlet):
    PATTERNS = [
        re.compile(
            r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/payments/robokassa/create$"
        ),
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.auth = hs.get_auth()

    async def on_POST(self, request: SynapseRequest, user_id: str) -> tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        module = get_module_instance()
        body = parse_json_object_from_request(request)
        plan_id = str(body.get("plan_id", "")).strip()
        plan = module.store.get_plan(plan_id)
        if plan is None:
            raise SynapseError(HTTPStatus.NOT_FOUND, "Plan not found", Codes.NOT_FOUND)

        payment = module.store.create_payment(user_id, plan)
        checkout_url = build_checkout_url(
            cfg=module.config,
            inv_id=int(payment["inv_id"]),
            amount=float(plan.price_rub),
            description=plan.name,
            payment_id=payment["payment_id"],
            user_id=user_id,
        )
        return 200, {
            "payment_id": payment["payment_id"],
            "checkout_url": checkout_url,
        }


class SetkaPlusPaymentStatusServlet(RestServlet):
    PATTERNS = [
        re.compile(
            r"^/_matrix/client/v3/user/(?P<user_id>[^/]+)/setka_plus/payments/(?P<payment_id>[^/]+)$"
        ),
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.auth = hs.get_auth()

    async def on_GET(
        self, request: SynapseRequest, user_id: str, payment_id: str
    ) -> tuple[int, JsonDict]:
        await _assert_self(request, self.auth, user_id)
        module = get_module_instance()
        payment = module.store.get_payment(payment_id, user_id=user_id)
        if payment is None:
            raise SynapseError(HTTPStatus.NOT_FOUND, "Payment not found", Codes.NOT_FOUND)
        return 200, {
            "payment_id": payment["payment_id"],
            "status": payment["status"],
            "plan_id": payment["plan_id"],
            "subscription_active": module.store.subscription_active(user_id),
            "message": payment.get("message"),
        }


async def _assert_self(request: SynapseRequest, auth, user_id: str) -> None:
    if not UserID.is_valid(user_id):
        raise SynapseError(HTTPStatus.BAD_REQUEST, "Invalid user id", Codes.INVALID_PARAM)
    requester = await auth.get_user_by_req(request)
    if requester.user.to_string() != user_id:
        raise SynapseError(HTTPStatus.FORBIDDEN, "Can only access your own Setka Plus data")


def register_servlets(hs: "HomeServer", http_server: HttpServer) -> None:
    SetkaPlusSubscriptionServlet(hs).register(http_server)
    SetkaPlusPlansServlet(hs).register(http_server)
    SetkaPlusCreatePaymentServlet(hs).register(http_server)
    SetkaPlusPaymentStatusServlet(hs).register(http_server)

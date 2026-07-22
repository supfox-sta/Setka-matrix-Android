from __future__ import annotations

from typing import Dict

from twisted.web.resource import Resource
from twisted.web.server import NOT_DONE_YET

from synapse.http.server import finish_request, respond_with_html
from synapse.http.site import SynapseRequest
from synapse.module_api import ModuleApi

from .config import SetkaPlusConfig
from .robokassa import (
    build_app_redirect,
    build_elementx_redirect,
    verify_result_signature,
    verify_success_signature,
)
from .store import SetkaPlusStore


def _respond_with_plain_text(request: SynapseRequest, code: int, body: str) -> None:
    """Robokassa ResultURL expects plain text like OK{InvId}."""
    payload = body.encode("utf-8")
    request.setResponseCode(code)
    if request._disconnected:
        return
    request.setHeader(b"Content-Type", b"text/plain; charset=utf-8")
    request.setHeader(b"Content-Length", b"%d" % (len(payload),))
    request.write(payload)
    finish_request(request)


def register_robokassa_resources(api: ModuleApi, store: SetkaPlusStore, config: SetkaPlusConfig) -> None:
    api.register_web_resource(
        "/setka/robokassa/result",
        RobokassaResultResource(store, config),
    )
    api.register_web_resource(
        "/setka/robokassa/success",
        RobokassaRedirectResource(store, config, success=True),
    )
    api.register_web_resource(
        "/setka/robokassa/fail",
        RobokassaRedirectResource(store, config, success=False),
    )
    api.register_web_resource(
        "/setka/robokassa/return/success",
        SetkaReturnResource(success=True),
    )
    api.register_web_resource(
        "/setka/robokassa/return/fail",
        SetkaReturnResource(success=False),
    )


def _extract_params(request: SynapseRequest) -> Dict[str, str]:
    params: Dict[str, str] = {}
    if request.args:
        for key, values in request.args.items():
            decoded_key = key.decode("utf-8")
            if values:
                params[decoded_key] = values[0].decode("utf-8")
    return params


class RobokassaResultResource(Resource):
    isLeaf = True

    def __init__(self, store: SetkaPlusStore, config: SetkaPlusConfig):
        super().__init__()
        self.store = store
        self.config = config

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
                from urllib.parse import unquote_plus

                request.args.setdefault(key.encode("utf-8"), []).append(
                    unquote_plus(value).encode("utf-8")
                )
        self._handle(request)
        return NOT_DONE_YET

    def _handle(self, request: SynapseRequest) -> None:
        params = _extract_params(request)
        out_sum = params.get("OutSum", "")
        inv_id = params.get("InvId", "")
        signature = params.get("SignatureValue", "")
        shp = {k: v for k, v in params.items() if k.lower().startswith("shp_")}

        if not out_sum or not inv_id or not signature:
            _respond_with_plain_text(request, 400, "Missing params")
            return
        if not verify_result_signature(
            out_sum, inv_id, signature, self.config.robokassa_password2, shp
        ):
            _respond_with_plain_text(request, 400, "Bad signature")
            return

        payment = self.store.get_payment_by_inv_id(int(inv_id))
        if payment is None:
            _respond_with_plain_text(request, 404, "Payment not found")
            return

        self.store.mark_payment_paid(payment["payment_id"])
        _respond_with_plain_text(request, 200, f"OK{inv_id}")


class RobokassaRedirectResource(Resource):
    isLeaf = True

    def __init__(self, store: SetkaPlusStore, config: SetkaPlusConfig, success: bool):
        super().__init__()
        self.store = store
        self.config = config
        self.success = success

    def render_GET(self, request: SynapseRequest) -> bytes:
        params = _extract_params(request)
        out_sum = params.get("OutSum", "")
        inv_id = params.get("InvId", "")
        signature = params.get("SignatureValue", "")
        shp = {k: v for k, v in params.items() if k.lower().startswith("shp_")}

        payment_id = shp.get("Shp_payment_id", "")
        user_id = shp.get("Shp_user_id", "")

        if self.success and out_sum and inv_id and signature:
            if not verify_success_signature(
                out_sum, inv_id, signature, self.config.robokassa_password1, shp
            ):
                request.redirect(
                    build_app_redirect(self.config, user_id, payment_id, success=False).encode(
                        "utf-8"
                    )
                )
                request.finish()
                return b""

        if inv_id and (not payment_id or not user_id):
            payment = self.store.get_payment_by_inv_id(int(inv_id))
            if payment:
                payment_id = payment["payment_id"]
                user_id = payment["user_id"]

        if not self.success and payment_id:
            self.store.mark_payment_failed(payment_id, "Оплата отменена")

        request.redirect(
            build_app_redirect(self.config, user_id, payment_id, success=self.success).encode(
                "utf-8"
            )
        )
        request.finish()
        return b""


class SetkaReturnResource(Resource):
    isLeaf = True

    def __init__(self, success: bool):
        super().__init__()
        self.success = success

    def render_GET(self, request: SynapseRequest) -> bytes:
        params = _extract_params(request)
        user_id = params.get("user_id", "")
        payment_id = params.get("payment_id", "")
        deeplink = build_elementx_redirect(user_id, payment_id, success=self.success)
        title = "Оплата прошла" if self.success else "Оплата не прошла"
        body = "Возвращаем в Setka…" if self.success else "Оплата не завершена."
        html = f"""<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8"><title>{title}</title>
<meta http-equiv="refresh" content="0;url={deeplink}"></head>
<body><p>{body}</p><p><a href="{deeplink}">Открыть приложение</a></p></body></html>"""
        respond_with_html(request, 200, html)
        return b""

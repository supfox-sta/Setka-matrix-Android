from __future__ import annotations

import hashlib
from typing import Dict, List, Optional
from urllib.parse import quote, urlencode

from .config import SetkaPlusConfig

ROBOKASSA_PAY_URL = "https://auth.robokassa.ru/Merchant/Index.aspx"


def md5_hex(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()


def sorted_shp_params(params: Dict[str, str]) -> List[tuple[str, str]]:
    shp = [(k, v) for k, v in params.items() if k.lower().startswith("shp_")]
    return sorted(shp, key=lambda item: item[0].lower())


def build_signature_base(*parts: str, shp: Optional[Dict[str, str]] = None) -> str:
    base_parts = list(parts)
    if shp:
        for key, value in sorted_shp_params(shp):
            base_parts.append(f"{key}={value}")
    return ":".join(base_parts)


def verify_result_signature(
    out_sum: str, inv_id: str, signature: str, password2: str, shp: Dict[str, str]
) -> bool:
    base = build_signature_base(out_sum, inv_id, password2, shp=shp)
    return md5_hex(base).upper() == signature.upper()


def verify_success_signature(
    out_sum: str, inv_id: str, signature: str, password1: str, shp: Dict[str, str]
) -> bool:
    base = build_signature_base(out_sum, inv_id, password1, shp=shp)
    return md5_hex(base).upper() == signature.upper()


def build_checkout_url(
    *,
    cfg: SetkaPlusConfig,
    inv_id: int,
    amount: float,
    description: str,
    payment_id: str,
    user_id: str,
) -> str:
    out_sum = f"{amount:.2f}"
    shp = {"Shp_payment_id": payment_id, "Shp_user_id": user_id}
    signature_base = build_signature_base(
        cfg.robokassa_merchant_login,
        out_sum,
        str(inv_id),
        cfg.robokassa_password1,
        shp=shp,
    )
    signature = md5_hex(signature_base)
    query = {
        "MerchantLogin": cfg.robokassa_merchant_login,
        "OutSum": out_sum,
        "InvId": str(inv_id),
        "Description": description,
        "SignatureValue": signature,
        "Shp_payment_id": payment_id,
        "Shp_user_id": user_id,
        "SuccessURL": f"{cfg.public_base_url}/setka/robokassa/success",
        "FailURL": f"{cfg.public_base_url}/setka/robokassa/fail",
        "ResultURL": f"{cfg.public_base_url}/setka/robokassa/result",
        "IsTest": "1" if cfg.robokassa_is_test else "0",
    }
    return f"{ROBOKASSA_PAY_URL}?{urlencode(query)}"


def build_app_redirect(cfg: SetkaPlusConfig, user_id: str, payment_id: str, success: bool) -> str:
    segment = "success" if success else "fail"
    params = urlencode({"user_id": user_id, "payment_id": payment_id})
    return f"{cfg.public_base_url}/setka/robokassa/return/{segment}?{params}"


def build_elementx_redirect(user_id: str, payment_id: str, success: bool) -> str:
    segment = "success" if success else "fail"
    encoded_user = quote(user_id, safe="")
    params = urlencode({"payment_id": payment_id})
    return f"elementx://open/{encoded_user}/setka-plus/payment/{segment}?{params}"

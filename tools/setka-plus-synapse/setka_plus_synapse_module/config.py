from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, List, Optional

from synapse.module_api import ModuleApi
from synapse.module_api.errors import ConfigError

from .synapse_config import get_server_config


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


def parse_config(raw: dict, api: ModuleApi | None) -> SetkaPlusConfig:
    merchant = _require_str(raw, "robokassa_merchant_login")
    password1 = _require_str(raw, "robokassa_password1")
    password2 = _require_str(raw, "robokassa_password2")
    is_test = bool(raw.get("robokassa_is_test", True))

    public_base_url = raw.get("public_base_url")
    if api is not None and not public_base_url:
        public_base_url = f"https://{get_server_config(api).server.server_name}"
    if api is None and not public_base_url:
        raise ConfigError("setka_plus.public_base_url is required in homeserver.yaml")
    public_base_url = str(public_base_url or "").rstrip("/")
    if not public_base_url.startswith("https://"):
        raise ConfigError("setka_plus.public_base_url must be https://...")

    plans_raw = raw.get("plans")
    if not isinstance(plans_raw, list) or not plans_raw:
        raise ConfigError("setka_plus.plans must be a non-empty list")

    plans: List[SetkaPlusPlanConfig] = []
    for index, item in enumerate(plans_raw):
        if not isinstance(item, dict):
            raise ConfigError(f"setka_plus.plans[{index}] must be an object")
        plan_id = _require_str(item, "id", context=f"Setka Plus plan[{index}]")
        name = _require_str(item, "name", context=f"Setka Plus plan[{index}]")
        price_rub = float(item.get("price_rub", 0))
        duration_days = int(item.get("duration_days", 0))
        if price_rub <= 0 or duration_days <= 0:
            raise ConfigError(f"setka_plus.plans[{index}] has invalid price/duration")
        features = item.get("features") or []
        if not isinstance(features, list):
            raise ConfigError(f"setka_plus.plans[{index}].features must be a list")
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

    return SetkaPlusConfig(
        robokassa_merchant_login=merchant,
        robokassa_password1=password1,
        robokassa_password2=password2,
        robokassa_is_test=is_test,
        public_base_url=public_base_url,
        plans=plans,
    )


def _require_str(raw: dict, key: str, *, context: str = "Setka Plus module") -> str:
    value = str(raw.get(key, "")).strip()
    if not value:
        raise ConfigError(f"{context}: '{key}' is required")
    return value

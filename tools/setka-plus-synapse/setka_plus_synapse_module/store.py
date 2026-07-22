from __future__ import annotations

import json
import os
import threading
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional

from synapse.module_api import ModuleApi

from .config import SetkaPlusConfig, SetkaPlusPlanConfig
from .synapse_config import get_server_config


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class SetkaPlusStore:
    """JSON files in Synapse data_dir — created automatically, no migrations."""

    def __init__(self, api: ModuleApi, config: SetkaPlusConfig):
        self.api = api
        self.config = config
        self._lock = threading.Lock()
        self._dir = os.path.join(get_server_config(api).server.data_dir, "setka_plus")
        self._payments_path = os.path.join(self._dir, "payments.json")
        self._subscriptions_path = os.path.join(self._dir, "subscriptions.json")
        self._meta_path = os.path.join(self._dir, "meta.json")
        os.makedirs(self._dir, exist_ok=True)
        self._ensure_files()

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
            data = self._read_payments()
            meta = self._read_meta()
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
                "created_at": utcnow().isoformat(),
                "paid_at": None,
                "message": None,
            }
            self._write_json(self._payments_path, data)
            self._write_json(self._meta_path, meta)
            return data[payment_id]

    def get_payment(self, payment_id: str, user_id: Optional[str] = None) -> Optional[Dict[str, Any]]:
        with self._lock:
            payment = self._read_payments().get(payment_id)
            if payment is None:
                return None
            if user_id is not None and payment.get("user_id") != user_id:
                return None
            return dict(payment)

    def get_payment_by_inv_id(self, inv_id: int) -> Optional[Dict[str, Any]]:
        with self._lock:
            for payment in self._read_payments().values():
                if int(payment.get("inv_id", -1)) == inv_id:
                    return dict(payment)
            return None

    def mark_payment_paid(self, payment_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            data = self._read_payments()
            payment = data.get(payment_id)
            if payment is None:
                return None
            if payment.get("status") == "paid":
                return dict(payment)
            payment["status"] = "paid"
            payment["paid_at"] = utcnow().isoformat()
            payment["message"] = None
            data[payment_id] = payment
            self._write_json(self._payments_path, data)
            plan = self.get_plan(payment["plan_id"])
            duration_days = plan.duration_days if plan else 30
            self._activate_subscription_locked(payment["user_id"], payment["plan_id"], duration_days)
            return dict(payment)

    def mark_payment_failed(self, payment_id: str, message: str) -> None:
        with self._lock:
            data = self._read_payments()
            payment = data.get(payment_id)
            if payment is None:
                return
            payment["status"] = "failed"
            payment["message"] = message
            data[payment_id] = payment
            self._write_json(self._payments_path, data)

    def get_subscription(self, user_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            sub = self._read_subscriptions().get(user_id)
            if sub is None:
                return None
            expires_at = datetime.fromisoformat(sub["expires_at"])
            if sub.get("status") != "active" or expires_at <= utcnow():
                return None
            return dict(sub)

    def subscription_active(self, user_id: str) -> bool:
        return self.get_subscription(user_id) is not None

    def _activate_subscription_locked(self, user_id: str, plan_id: str, duration_days: int) -> None:
        subs = self._read_subscriptions()
        now = utcnow()
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

    def _read_payments(self) -> Dict[str, Any]:
        return self._read_json(self._payments_path)

    def _read_subscriptions(self) -> Dict[str, Any]:
        return self._read_json(self._subscriptions_path)

    def _read_meta(self) -> Dict[str, Any]:
        return self._read_json(self._meta_path)

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
        temp_path = f"{path}.tmp"
        with open(temp_path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
        os.replace(temp_path, path)

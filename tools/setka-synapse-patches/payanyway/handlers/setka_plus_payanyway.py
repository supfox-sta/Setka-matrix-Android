# Copy methods into synapse/handlers/setka_plus.py (SetkaPlusHandler class).
# Add __init__ fields and apply_server_config keys mirroring robokassa block.

from __future__ import annotations

import hashlib
import uuid
from typing import TYPE_CHECKING
from urllib.parse import urlencode

from synapse.api.errors import Codes, SynapseError

if TYPE_CHECKING:
    from synapse.types import JsonDict


def _payanyway_md5(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()


class SetkaPlusPayAnyWayMethods:
    """Mixin-style reference — merge into SetkaPlusHandler."""

    async def create_payanyway_payment_request(
        self,
        user_id: str,
        *,
        amount: float | None = None,
        description: str | None = None,
        plan_id: str | None = None,
    ) -> JsonDict:
        mnt_id = self._payanyway_mnt_id
        integrity = self._payanyway_integrity_code
        if not mnt_id or not integrity:
            raise SynapseError(
                500,
                "SETKA_PLUS_PAYANYWAY is not configured",
                Codes.UNKNOWN,
            )

        plan = self._resolve_plan(plan_id)
        selected_plan_id = str(plan.get("id", "setka_plus"))
        selected_plan_name = str(plan.get("name", "Setka Plus"))
        final_amount = round(
            amount if amount and amount > 0 else float(plan.get("price_rub", self._plus_price_rub)),
            2,
        )
        comment = description or f"{selected_plan_name} subscription"
        payment_id = f"setka_plus_{uuid.uuid4().hex}"
        subscriber_id = self._encode_robokassa_uid(user_id)  # reuse url-safe uid codec
        amount_str = f"{final_amount:.2f}"
        test_mode = "1" if self._payanyway_is_test else "0"

        signature = _payanyway_md5(
            mnt_id
            + payment_id
            + amount_str
            + "RUB"
            + subscriber_id
            + test_mode
            + integrity
        )

        query = {
            "MNT_ID": mnt_id,
            "MNT_TRANSACTION_ID": payment_id,
            "MNT_AMOUNT": amount_str,
            "MNT_CURRENCY_CODE": "RUB",
            "MNT_TEST_MODE": test_mode,
            "MNT_DESCRIPTION": comment[:500],
            "MNT_SUBSCRIBER_ID": subscriber_id,
            "MNT_CUSTOM1": selected_plan_id[:32],
            "MNT_CUSTOM2": payment_id[:32],
            "MNT_SIGNATURE": signature,
        }
        checkout_url = self._payanyway_payment_url + "?" + urlencode(query)

        now = _now_ms()
        await self.record_payment(
            user_id,
            {
                "payment_id": payment_id,
                "request_id": payment_id,
                "provider": "payanyway",
                "status": "pending",
                "amount": final_amount,
                "currency": "RUB",
                "label": f"payanyway:{payment_id}",
                "plan_id": selected_plan_id,
                "created_at": now,
            },
        )
        await self.set_subscription(
            user_id,
            {"status": "pending", "tier": selected_plan_id, "amount": final_amount},
        )

        return {
            "payment_id": payment_id,
            "request_id": payment_id,
            "provider": "payanyway",
            "status": "created",
            "amount": final_amount,
            "currency": "RUB",
            "label": f"payanyway:{payment_id}",
            "plan_id": selected_plan_id,
            "plan_name": selected_plan_name,
            "checkout_url": checkout_url,
            "return_url": "https://setka-matrix.ru/",
        }

    async def handle_payanyway_pay_url(self, payload: dict[str, str]) -> str:
        """Pay URL — respond SUCCESS or FAIL (plain text, HTTP 200)."""
        if not payload:
            return "SUCCESS"  # Moneta health check

        expected = self._payanyway_pay_signature(payload)
        if payload.get("MNT_SIGNATURE", "").lower() != expected.lower():
            raise SynapseError(403, "Invalid PayAnyWay signature", Codes.FORBIDDEN)

        payment_id = payload.get("MNT_TRANSACTION_ID", "").strip()
        user_id = self._decode_robokassa_uid(payload.get("MNT_SUBSCRIBER_ID", "").strip())
        plan_id = payload.get("MNT_CUSTOM1", "").strip() or "setka_plus"
        amount = _clean_float(payload.get("MNT_AMOUNT", "")) or self._plus_price_rub

        if not payment_id or not user_id:
            return "FAIL"

        await self.activate_subscription(
            user_id,
            payment_id=payment_id,
            payment_provider="payanyway",
            amount=amount,
            plan_id=plan_id,
            currency="RUB",
        )
        await self.record_payment(
            user_id,
            {
                "payment_id": payment_id,
                "request_id": payment_id,
                "provider": "payanyway",
                "status": "paid",
                "amount": amount,
                "currency": "RUB",
                "label": f"payanyway:{payment_id}",
                "plan_id": plan_id,
                "created_at": _now_ms(),
            },
        )
        return "SUCCESS"

    async def handle_payanyway_check_url(self, payload: dict[str, str]) -> str:
        """Check URL — XML MNT_RESPONSE."""
        mnt_id = self._payanyway_mnt_id
        integrity = self._payanyway_integrity_code
        tx_id = payload.get("MNT_TRANSACTION_ID", "").strip()
        if not tx_id:
            return self._payanyway_check_xml(mnt_id, "", "500", "Missing transaction id", "0.00", integrity)

        if payload.get("MNT_COMMAND", "").upper() != "CHECK":
            return self._payanyway_check_xml(mnt_id, tx_id, "500", "Unsupported command", "0.00", integrity)

        expected = self._payanyway_check_signature(payload)
        if payload.get("MNT_SIGNATURE", "").lower() != expected.lower():
            return self._payanyway_check_xml(mnt_id, tx_id, "500", "Invalid signature", "0.00", integrity)

        user_id = self._decode_robokassa_uid(payload.get("MNT_SUBSCRIBER_ID", "").strip())
        if not user_id:
            return self._payanyway_check_xml(mnt_id, tx_id, "500", "Unknown user", "0.00", integrity)

        status = await self.get_payment_status(user_id, tx_id)
        if status and status.get("status") == "paid":
            amount = f"{float(status.get('amount', self._plus_price_rub)):.2f}"
            return self._payanyway_check_xml(mnt_id, tx_id, "200", "Already paid", amount, integrity)

        pending = await self.find_payment_by_id(user_id, tx_id)
        amount_raw = payload.get("MNT_AMOUNT", "").strip()
        if amount_raw:
            amount = f"{float(amount_raw):.2f}"
        elif pending:
            amount = f"{float(pending.get('amount', self._plus_price_rub)):.2f}"
        else:
            amount = f"{self._plus_price_rub:.2f}"

        return self._payanyway_check_xml(mnt_id, tx_id, "402", "Ready for payment", amount, integrity)

    def build_payanyway_return_url(self, user_id: str, payment_id: str, *, success: bool) -> str:
        from urllib.parse import quote

        segment = "success" if success else "fail"
        return (
            f"{self._payanyway_callback_base_url}/return/{segment}"
            f"?user_id={quote(user_id)}&payment_id={quote(payment_id)}"
        )

    def _payanyway_pay_signature(self, payload: dict[str, str]) -> str:
        parts = [
            payload.get("MNT_ID", ""),
            payload.get("MNT_TRANSACTION_ID", ""),
            payload.get("MNT_OPERATION_ID", ""),
            f"{float(payload.get('MNT_AMOUNT', '0') or 0):.2f}",
            payload.get("MNT_CURRENCY_CODE", ""),
            payload.get("MNT_SUBSCRIBER_ID", ""),
            payload.get("MNT_TEST_MODE", "0"),
            self._payanyway_integrity_code,
        ]
        return _payanyway_md5("".join(parts))

    def _payanyway_check_signature(self, payload: dict[str, str]) -> str:
        amount_raw = payload.get("MNT_AMOUNT", "").strip()
        amount_part = f"{float(amount_raw):.2f}" if amount_raw else ""
        parts = [
            payload.get("MNT_COMMAND", ""),
            payload.get("MNT_ID", ""),
            payload.get("MNT_TRANSACTION_ID", ""),
            payload.get("MNT_OPERATION_ID", ""),
            amount_part,
            payload.get("MNT_CURRENCY_CODE", ""),
            payload.get("MNT_SUBSCRIBER_ID", ""),
            payload.get("MNT_TEST_MODE", "0"),
            self._payanyway_integrity_code,
        ]
        return _payanyway_md5("".join(parts))

    @staticmethod
    def _payanyway_check_xml(
        mnt_id: str,
        tx_id: str,
        result_code: str,
        description: str,
        amount: str,
        integrity: str,
    ) -> str:
        signature = _payanyway_md5(result_code + mnt_id + tx_id + integrity)
        return (
            '<?xml version="1.0" encoding="UTF-8"?>'
            "<MNT_RESPONSE>"
            f"<MNT_ID>{mnt_id}</MNT_ID>"
            f"<MNT_TRANSACTION_ID>{tx_id}</MNT_TRANSACTION_ID>"
            f"<MNT_RESULT_CODE>{result_code}</MNT_RESULT_CODE>"
            f"<MNT_DESCRIPTION>{description}</MNT_DESCRIPTION>"
            f"<MNT_AMOUNT>{amount}</MNT_AMOUNT>"
            f"<MNT_SIGNATURE>{signature}</MNT_SIGNATURE>"
            "</MNT_RESPONSE>"
        )

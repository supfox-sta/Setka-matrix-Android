# This file is licensed under the Affero General Public License (AGPL) version 3.
# Copyright (C) 2026 Setka Matrix

"""Setka user privacy settings stored in account data."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from synapse.api.errors import Codes, SynapseError
from synapse.types import JsonDict, UserID

if TYPE_CHECKING:
    from synapse.server import HomeServer

logger = logging.getLogger(__name__)

ACCOUNT_DATA_TYPE = "io.setka.privacy"
VISIBILITY_VALUES = frozenset({"everyone", "contacts", "nobody"})
DEFAULT_PRIVACY: JsonDict = {
    "discoverable_email": True,
    "discoverable_msisdn": True,
    "email_visibility": "everyone",
    "phone_visibility": "everyone",
    "last_seen_visibility": "everyone",
    "call_visibility": "everyone",
}


def _normalize_visibility(value: object, *, default: str) -> str:
    if isinstance(value, str) and value in VISIBILITY_VALUES:
        return value
    return default


def _normalize_privacy(raw: JsonDict | None) -> JsonDict:
    data = raw or {}
    return {
        "discoverable_email": bool(data.get("discoverable_email", DEFAULT_PRIVACY["discoverable_email"])),
        "discoverable_msisdn": bool(data.get("discoverable_msisdn", DEFAULT_PRIVACY["discoverable_msisdn"])),
        "email_visibility": _normalize_visibility(
            data.get("email_visibility"), default=DEFAULT_PRIVACY["email_visibility"]
        ),
        "phone_visibility": _normalize_visibility(
            data.get("phone_visibility"), default=DEFAULT_PRIVACY["phone_visibility"]
        ),
        "last_seen_visibility": _normalize_visibility(
            data.get("last_seen_visibility"), default=DEFAULT_PRIVACY["last_seen_visibility"]
        ),
        "call_visibility": _normalize_visibility(
            data.get("call_visibility"), default=DEFAULT_PRIVACY["call_visibility"]
        ),
    }


class SetkaPrivacyHandler:
    def __init__(self, hs: "HomeServer"):
        self._hs = hs
        self._store = hs.get_datastores().main
        self._account_data_handler = hs.get_account_data_handler()
        logger.info("Setka privacy handler registered")

    async def get_privacy(self, user_id: str) -> JsonDict:
        raw = await self._account_data_handler.get_account_data_for_user(
            user_id, ACCOUNT_DATA_TYPE
        )
        return _normalize_privacy(raw)

    async def set_privacy(self, user_id: str, updates: JsonDict) -> JsonDict:
        current = await self.get_privacy(user_id)
        merged = dict(current)
        if "discoverable_email" in updates:
            merged["discoverable_email"] = bool(updates["discoverable_email"])
        if "discoverable_msisdn" in updates:
            merged["discoverable_msisdn"] = bool(updates["discoverable_msisdn"])
        for key in (
            "email_visibility",
            "phone_visibility",
            "last_seen_visibility",
            "call_visibility",
        ):
            if key in updates:
                merged[key] = _normalize_visibility(updates[key], default=current[key])
        await self._account_data_handler.add_account_data_for_user(
            user_id, ACCOUNT_DATA_TYPE, merged
        )
        return merged

    async def can_user_call(self, caller_id: str, callee_id: str) -> bool:
        privacy = await self.get_privacy(callee_id)
        visibility = privacy.get("call_visibility", "everyone")
        if visibility == "everyone":
            return True
        if visibility == "nobody":
            return False
        return await self._is_contact(caller_id, callee_id)

    async def _is_contact(self, caller_id: str, callee_id: str) -> bool:
        caller = UserID.from_string(caller_id)
        callee = UserID.from_string(callee_id)
        if caller.domain != callee.domain:
            return False
        room_id = await self._store.get_local_user_direct_room(caller.to_string(), callee.to_string())
        if room_id is None:
            room_id = await self._store.get_local_user_direct_room(callee.to_string(), caller.to_string())
        return room_id is not None

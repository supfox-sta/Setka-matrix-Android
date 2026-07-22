# Copyright 2026 Setka Matrix
# SPDX-License-Identifier: AGPL-3.0-only
#
# Standalone REST patch (copy to site-packages). For integrated Setka Synapse trees,
# use synapse/handlers/setka_channels.py + rest/client/setka_channels.py instead.
#
# See element-x-android docs/setka-lite/setka-channels-synapse.md

from __future__ import annotations

import logging
import re
from http import HTTPStatus
from typing import TYPE_CHECKING, Any, Optional, Tuple

from synapse.api.constants import EventTypes
from synapse.api.errors import Codes, SynapseError
from synapse.events import EventBase
from synapse.http.server import HttpServer
from synapse.http.servlet import RestServlet, parse_integer, parse_string
from synapse.http.site import SynapseRequest
from synapse.types import JsonDict, StateMap

if TYPE_CHECKING:
    from synapse.server import HomeServer

logger = logging.getLogger(__name__)

CHANNEL_TOPIC_PREFIX = "setka:channel"
MODERATOR_POWER_LEVEL = 50
_FEED_EVENT_TYPES = frozenset({"m.room.message", "m.sticker"})


def _topic_from_state(state: StateMap[EventBase]) -> str:
    topic_event = state.get((EventTypes.Topic, ""))
    if topic_event is None:
        return ""
    content = topic_event.content
    if isinstance(content, dict):
        return str(content.get("topic") or "")
    return ""


def _is_setka_channel_state(state: StateMap[EventBase]) -> bool:
    topic = _topic_from_state(state).strip()
    return topic.lower().startswith(CHANNEL_TOPIC_PREFIX.lower())


def _is_threaded_content(content: object) -> bool:
    if not isinstance(content, dict):
        return False
    relates = content.get("m.relates_to")
    if not isinstance(relates, dict):
        return False
    if relates.get("rel_type") == "m.thread":
        return True
    in_reply = relates.get("m.in_reply_to")
    if isinstance(in_reply, dict) and in_reply.get("event_id"):
        return True
    return False


def _sender_power_level(state: StateMap[EventBase], sender: str) -> int:
    pl_event = state.get((EventTypes.PowerLevels, ""))
    if pl_event is None:
        return 0
    content = pl_event.content
    if not isinstance(content, dict):
        return 0
    users = content.get("users") or {}
    if isinstance(users, dict) and sender in users:
        return int(users[sender])
    return int(content.get("users_default") or 0)


async def _check_event_allowed(
    event: EventBase, state: StateMap[EventBase]
) -> Tuple[bool, Optional[dict]]:
    return True, None


def install(hs: "HomeServer") -> None:
    hs.get_module_api().register_third_party_rules_callbacks(
        check_event_allowed=_check_event_allowed,
    )
    logger.info("Setka channels event rules installed (topic prefix=%s)", CHANNEL_TOPIC_PREFIX)


def _room_entry_to_channel(room: JsonDict) -> Optional[JsonDict]:
    topic = str(room.get("topic") or "")
    if not topic.lower().startswith(CHANNEL_TOPIC_PREFIX.lower()):
        return None
    return {
        "room_id": room.get("room_id"),
        "name": room.get("name"),
        "topic": topic,
        "avatar_url": room.get("avatar_url"),
        "num_joined_members": room.get("num_joined_members", 0),
        "canonical_alias": room.get("canonical_alias"),
        "aliases": room.get("aliases") or [],
    }


class SetkaChannelsPublicServlet(RestServlet):
    PATTERNS = [
        re.compile(r"^/_matrix/client/v3/setka/channels/public$"),
    ]

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self.hs = hs
        self.auth = hs.get_auth()
        self.room_list_handler = hs.get_room_list_handler()

    async def on_GET(self, request: SynapseRequest) -> Tuple[int, JsonDict]:
        await self.auth.get_user_by_req(request)
        limit = parse_integer(request, "limit", 40)
        since = parse_string(request, "since")
        if limit == 0:
            limit = None
        data = await self.room_list_handler.get_local_public_room_list(
            limit=limit,
            since_token=since,
            search_filter={"generic_search_term": "setka"},
        )
        channels = []
        for room in data.get("chunk") or []:
            if isinstance(room, dict):
                entry = _room_entry_to_channel(room)
                if entry is not None:
                    channels.append(entry)
        return 200, {
            "channels": channels,
            "next_batch": data.get("next_batch"),
            "total_room_count_estimate": len(channels),
        }


def register_servlets(hs: "HomeServer", http_server: HttpServer) -> None:
    install(hs)
    SetkaChannelsPublicServlet(hs).register(http_server)
    logger.info("Setka channels Matrix REST servlets registered")

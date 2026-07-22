"""
Setka channel rooms: only moderators+ post on the main timeline; members may post in threads.

Mark channels with m.room.topic prefix `setka:channel` (see SetkaChannelConfig in the Android app).
"""

from __future__ import annotations

import logging
from typing import Any, Optional, Tuple

from synapse.module_api import ModuleApi

logger = logging.getLogger(__name__)

CHANNEL_TOPIC_PREFIX = "setka:channel"
MODERATOR_POWER_LEVEL = 50

# Event types that count as «feed» posts (must match moderator PL when not in a thread).
_FEED_EVENT_TYPES = frozenset(
    {
        "m.room.message",
        "m.sticker",
    }
)


def _topic_from_state(state: Any) -> str:
    topic_event = state.get(("m.room.topic", ""))
    if not topic_event:
        return ""
    content = getattr(topic_event, "content", None) or {}
    if isinstance(content, dict):
        return str(content.get("topic") or "")
    return ""


def _is_setka_channel_state(state: Any) -> bool:
    topic = _topic_from_state(state).strip()
    return topic.lower().startswith(CHANNEL_TOPIC_PREFIX.lower())


def _is_threaded_content(content: Any) -> bool:
    if not isinstance(content, dict):
        return False
    relates = content.get("m.relates_to")
    if not isinstance(relates, dict):
        return False
    if relates.get("rel_type") == "m.thread":
        return True
    # Some clients only set in_reply_to on thread replies.
    in_reply = relates.get("m.in_reply_to")
    if isinstance(in_reply, dict) and in_reply.get("event_id"):
        return True
    return False


def _sender_power_level(state: Any, sender: str) -> int:
    pl_event = state.get(("m.room.power_levels", ""))
    if not pl_event:
        return 0
    content = getattr(pl_event, "content", None) or {}
    if not isinstance(content, dict):
        return 0
    users = content.get("users") or {}
    if isinstance(users, dict) and sender in users:
        return int(users[sender])
    return int(content.get("users_default") or 0)


class SetkaChannelsModule:
    """Allow thread / comment sends for channel subscribers blocked by events_default."""

    def __init__(self, config: dict, api: ModuleApi):
        self.api = api
        api.register_third_party_rules_callbacks(check_event_allowed=self.check_event_allowed)
        logger.info("Setka channels module loaded (topic prefix=%s)", CHANNEL_TOPIC_PREFIX)

    @staticmethod
    def parse_config(config: dict) -> dict:
        return config or {}

    async def check_event_allowed(
        self,
        event: Any,
        state: Any,
    ) -> Tuple[bool, Optional[str]]:
        if event.type not in _FEED_EVENT_TYPES:
            return True, None
        if not _is_setka_channel_state(state):
            return True, None
        content = getattr(event, "content", None) or {}
        if _is_threaded_content(content):
            return True, None
        sender = event.sender
        if _sender_power_level(state, sender) >= MODERATOR_POWER_LEVEL:
            return True, None
        return (
            False,
            "Only moderators can post on the channel timeline; use comments (thread replies).",
        )

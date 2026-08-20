# This file is licensed under the Affero General Public License (AGPL) version 3.
# Copyright (C) 2026 Setka Matrix

from __future__ import annotations

from typing import TYPE_CHECKING

from synapse.api.errors import Codes, SynapseError
from synapse.http.servlet import RestServlet, parse_json_object_from_request
from synapse.rest.client._base import client_patterns
from synapse.types import JsonDict

if TYPE_CHECKING:
    from synapse.http.site import SynapseRequest
    from synapse.server import HomeServer


class SetkaPrivacyServlet(RestServlet):
    PATTERNS = client_patterns("/user/(?P<user_id>[^/]*)/setka_privacy$")

    def __init__(self, hs: "HomeServer"):
        super().__init__()
        self._auth = hs.get_auth()
        self._handler = hs.get_setka_privacy_handler()

    async def on_GET(
        self, request: "SynapseRequest", user_id: str
    ) -> tuple[int, JsonDict]:
        requester = await self._auth.get_user_by_req(request)
        if requester.user.to_string() != user_id:
            raise SynapseError(403, "Can only read your own privacy settings", Codes.FORBIDDEN)
        return 200, await self._handler.get_privacy(user_id)

    async def on_PUT(
        self, request: "SynapseRequest", user_id: str
    ) -> tuple[int, JsonDict]:
        requester = await self._auth.get_user_by_req(request)
        if requester.user.to_string() != user_id:
            raise SynapseError(403, "Can only update your own privacy settings", Codes.FORBIDDEN)
        body = parse_json_object_from_request(request)
        return 200, await self._handler.set_privacy(user_id, body)


def register_servlets(hs: "HomeServer", http_server) -> None:
    hs.get_setka_privacy_handler()
    SetkaPrivacyServlet(hs).register(http_server)

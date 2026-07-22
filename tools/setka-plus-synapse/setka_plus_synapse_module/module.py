"""
Setka Plus Synapse module entry point.

Install:
  pip install /path/to/tools/setka-plus-synapse

Configure homeserver.yaml (see README.md), restart Synapse.
"""

from __future__ import annotations

import logging
from typing import Any, MutableSequence

from synapse.module_api import ModuleApi
from synapse.module_api.errors import ConfigError

from .callbacks import register_robokassa_resources
from .client import register_servlets
from .config import SetkaPlusConfig, parse_config
from .runtime import set_module_instance
from .store import SetkaPlusStore

logger = logging.getLogger(__name__)


def _patch_client_servlets() -> None:
    """Attach Setka Plus servlets to Matrix Client REST (runs before listeners start)."""
    import synapse.rest as synapse_rest

    functions: MutableSequence[Any] = list(synapse_rest.CLIENT_SERVLET_FUNCTIONS)
    if register_servlets not in functions:
        functions.append(register_servlets)
        synapse_rest.CLIENT_SERVLET_FUNCTIONS = tuple(functions)
        logger.info("Setka Plus: registered client servlets hook")


class SetkaPlusModule:
    """Drop-in Synapse module for Setka Plus + Robokassa."""

    def __init__(self, config: dict, api: ModuleApi):
        self.api = api
        self.config: SetkaPlusConfig = parse_config(config, api)
        self.store = SetkaPlusStore(api, self.config)

        set_module_instance(self)
        _patch_client_servlets()
        register_robokassa_resources(api, self.store, self.config)

        logger.info(
            "Setka Plus module loaded (public_base_url=%s, plans=%d)",
            self.config.public_base_url,
            len(self.config.plans),
        )

    @staticmethod
    def parse_config(config: dict) -> dict:
        # Validate early at Synapse startup; api is not available here yet.
        parse_config(config, api=None)
        return config

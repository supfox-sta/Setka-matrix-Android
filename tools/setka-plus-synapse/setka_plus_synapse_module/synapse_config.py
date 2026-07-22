from __future__ import annotations

from synapse.module_api import ModuleApi


def get_server_config(api: ModuleApi):
    """ModuleApi does not expose .config; use the underlying HomeServer."""
    return api._hs.config

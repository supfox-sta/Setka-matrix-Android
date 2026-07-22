from __future__ import annotations

from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from .module import SetkaPlusModule

_MODULE: Optional["SetkaPlusModule"] = None


def set_module_instance(module: "SetkaPlusModule") -> None:
    global _MODULE
    _MODULE = module


def get_module_instance() -> "SetkaPlusModule":
    if _MODULE is None:
        raise RuntimeError("Setka Plus module is not initialised")
    return _MODULE

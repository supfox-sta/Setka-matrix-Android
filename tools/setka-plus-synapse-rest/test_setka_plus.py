#!/usr/bin/env python3
"""Unit tests for Setka Plus Robokassa helpers. Run on Synapse server venv:

    /opt/venvs/matrix-synapse/bin/python test_setka_plus.py

Or from repo after copying setka_plus.py next to this file.
"""

from __future__ import annotations

import hashlib
import importlib.util
import os
import sys
import unittest


def _load_module():
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "setka_plus.py")
    spec = importlib.util.spec_from_file_location("setka_plus", path)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


class SetkaPlusRobokassaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_module()

    def test_result_signature_with_shp(self):
        out_sum = "199.00"
        inv_id = "42"
        password2 = "test_password_2"
        shp = {
            "Shp_payment_id": "pay-abc123",
            "Shp_user_id": "@user:example.com",
        }
        base = f"{out_sum}:{inv_id}:{password2}:Shp_payment_id=pay-abc123:Shp_user_id=@user:example.com"
        sig = hashlib.md5(base.encode()).hexdigest().upper()
        self.assertTrue(
            self.mod.verify_result_signature(out_sum, inv_id, sig, password2, shp)
        )
        self.assertFalse(
            self.mod.verify_result_signature(out_sum, inv_id, "BAD", password2, shp)
        )

    def test_success_signature(self):
        out_sum = "199.00"
        inv_id = "7"
        password1 = "test_password_1"
        shp = {"Shp_payment_id": "pay-x", "Shp_user_id": "@a:b"}
        base = f"{out_sum}:{inv_id}:{password1}:Shp_payment_id=pay-x:Shp_user_id=@a:b"
        sig = hashlib.md5(base.encode()).hexdigest().upper()
        self.assertTrue(
            self.mod.verify_success_signature(out_sum, inv_id, sig, password1, shp)
        )


if __name__ == "__main__":
    unittest.main()

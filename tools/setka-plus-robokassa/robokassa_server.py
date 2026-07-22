"""
Reference Robokassa + Setka Plus payment server.

Deploy behind https://matrix.setka-matrix.ru or integrate into Synapse module.
See README.md for Robokassa merchant settings.
"""

from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import quote, urlencode

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, PlainTextResponse, RedirectResponse

MERCHANT_LOGIN = os.environ.get("ROBOKASSA_MERCHANT_LOGIN", "demo")
PASSWORD1 = os.environ.get("ROBOKASSA_PASSWORD1", "password1")
PASSWORD2 = os.environ.get("ROBOKASSA_PASSWORD2", "password2")
IS_TEST = os.environ.get("ROBOKASSA_IS_TEST", "1") == "1"
PUBLIC_BASE_URL = os.environ.get("SETKA_PUBLIC_BASE_URL", "https://matrix.setka-matrix.ru").rstrip("/")
DB_PATH = os.environ.get("SETKA_DB_PATH", "setka_plus.db")
ROBOKASSA_PAY_URL = "https://auth.robokassa.ru/Merchant/Index.aspx"

app = FastAPI(title="Setka Plus Robokassa Reference")


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


@contextmanager
def db_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with db_conn() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS payments (
                payment_id TEXT PRIMARY KEY,
                inv_id INTEGER NOT NULL UNIQUE,
                user_id TEXT NOT NULL,
                plan_id TEXT NOT NULL,
                amount REAL NOT NULL,
                description TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                paid_at TEXT,
                message TEXT
            );
            CREATE TABLE IF NOT EXISTS subscriptions (
                user_id TEXT PRIMARY KEY,
                plan_id TEXT NOT NULL,
                status TEXT NOT NULL,
                started_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS plans (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                price_rub REAL NOT NULL,
                duration_days INTEGER NOT NULL,
                features_json TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                sort_order INTEGER NOT NULL DEFAULT 0
            );
            """
        )
        if conn.execute("SELECT COUNT(*) FROM plans").fetchone()[0] == 0:
            conn.execute(
                """
                INSERT INTO plans (id, name, price_rub, duration_days, features_json, active, sort_order)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """,
                (
                    "setka_plus_month",
                    "Setka Plus — 30 дней",
                    199.0,
                    30,
                    '["Стикеры и эмодзи","Обмен паками","Новые функции"]',
                    0,
                ),
            )


def md5_hex(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()


def sorted_shp_params(params: dict[str, str]) -> list[tuple[str, str]]:
    shp = [(k, v) for k, v in params.items() if k.lower().startswith("shp_")]
    return sorted(shp, key=lambda item: item[0].lower())


def build_signature_base(*parts: str, shp: dict[str, str] | None = None) -> str:
    base_parts = list(parts)
    if shp:
        for key, value in sorted_shp_params(shp):
            base_parts.append(f"{key}={value}")
    return ":".join(base_parts)


def verify_result_signature(out_sum: str, inv_id: str, signature: str, shp: dict[str, str]) -> bool:
    base = build_signature_base(out_sum, inv_id, PASSWORD2, shp=shp)
    return md5_hex(base).upper() == signature.upper()


def verify_success_signature(out_sum: str, inv_id: str, signature: str, shp: dict[str, str]) -> bool:
    base = build_signature_base(out_sum, inv_id, PASSWORD1, shp=shp)
    return md5_hex(base).upper() == signature.upper()


def next_inv_id(conn: sqlite3.Connection) -> int:
    row = conn.execute("SELECT COALESCE(MAX(inv_id), 0) + 1 AS next_id FROM payments").fetchone()
    return int(row["next_id"])


def activate_subscription(conn: sqlite3.Connection, user_id: str, plan_id: str, duration_days: int) -> None:
    now = utcnow()
    expires = now + timedelta(days=duration_days)
    conn.execute(
        """
        INSERT INTO subscriptions (user_id, plan_id, status, started_at, expires_at, updated_at)
        VALUES (?, ?, 'active', ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            plan_id=excluded.plan_id,
            status='active',
            started_at=excluded.started_at,
            expires_at=excluded.expires_at,
            updated_at=excluded.updated_at
        """,
        (
            user_id,
            plan_id,
            now.isoformat(),
            expires.isoformat(),
            now.isoformat(),
        ),
    )


def subscription_active(conn: sqlite3.Connection, user_id: str) -> bool:
    row = conn.execute(
        "SELECT status, expires_at FROM subscriptions WHERE user_id = ?",
        (user_id,),
    ).fetchone()
    if row is None:
        return False
    if row["status"] != "active":
        return False
    expires_at = datetime.fromisoformat(row["expires_at"])
    return expires_at > utcnow()


def matrix_user_path(user_id: str, suffix: str) -> str:
    return f"/_matrix/client/v3/user/{quote(user_id, safe='')}/setka_plus{suffix}"


def build_checkout_url(
    *,
    inv_id: int,
    amount: float,
    description: str,
    payment_id: str,
    user_id: str,
) -> str:
    out_sum = f"{amount:.2f}"
    shp = {"Shp_payment_id": payment_id, "Shp_user_id": user_id}
    signature_base = build_signature_base(MERCHANT_LOGIN, out_sum, str(inv_id), PASSWORD1, shp=shp)
    signature = md5_hex(signature_base)
    query = {
        "MerchantLogin": MERCHANT_LOGIN,
        "OutSum": out_sum,
        "InvId": str(inv_id),
        "Description": description,
        "SignatureValue": signature,
        "Shp_payment_id": payment_id,
        "Shp_user_id": user_id,
        "SuccessURL": f"{PUBLIC_BASE_URL}/setka/robokassa/success",
        "FailURL": f"{PUBLIC_BASE_URL}/setka/robokassa/fail",
        "ResultURL": f"{PUBLIC_BASE_URL}/setka/robokassa/result",
        "IsTest": "1" if IS_TEST else "0",
    }
    return f"{ROBOKASSA_PAY_URL}?{urlencode(query)}"


def build_app_redirect(user_id: str, payment_id: str, success: bool) -> str:
    segment = "success" if success else "fail"
    params = urlencode({"user_id": user_id, "payment_id": payment_id})
    return f"{PUBLIC_BASE_URL}/setka/robokassa/return/{segment}?{params}"


def build_elementx_redirect(user_id: str, payment_id: str, success: bool) -> str:
    segment = "success" if success else "fail"
    encoded_user = quote(user_id, safe="")
    params = urlencode({"payment_id": payment_id})
    return f"elementx://open/{encoded_user}/setka-plus/payment/{segment}?{params}"


@app.on_event("startup")
def on_startup() -> None:
    init_db()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get(matrix_user_path("{user_id}", "/subscription"))
def get_subscription(user_id: str) -> dict[str, Any]:
    with db_conn() as conn:
        sub = conn.execute(
            "SELECT * FROM subscriptions WHERE user_id = ?",
            (user_id,),
        ).fetchone()
        if sub is None or not subscription_active(conn, user_id):
            return {
                "tier": "setka_plus",
                "plan_name": None,
                "status": "inactive",
                "is_active": False,
            }
        plan = conn.execute("SELECT name FROM plans WHERE id = ?", (sub["plan_id"],)).fetchone()
        return {
            "tier": sub["plan_id"],
            "plan_name": plan["name"] if plan else sub["plan_id"],
            "status": "active",
            "is_active": True,
            "started_at": int(datetime.fromisoformat(sub["started_at"]).timestamp() * 1000),
            "expires_at": int(datetime.fromisoformat(sub["expires_at"]).timestamp() * 1000),
            "updated_at": int(datetime.fromisoformat(sub["updated_at"]).timestamp() * 1000),
        }


@app.get(matrix_user_path("{user_id}", "/plans"))
def get_plans(user_id: str) -> dict[str, Any]:
    with db_conn() as conn:
        rows = conn.execute(
            "SELECT * FROM plans WHERE active = 1 ORDER BY sort_order, price_rub"
        ).fetchall()
        plans = []
        for row in rows:
            plans.append(
                {
                    "id": row["id"],
                    "name": row["name"],
                    "price_rub": row["price_rub"],
                    "duration_days": row["duration_days"],
                    "features": json.loads(row["features_json"]),
                    "active": bool(row["active"]),
                    "sort_order": row["sort_order"],
                }
            )
        return {"plans": plans}


@app.post(matrix_user_path("{user_id}", "/payments/robokassa/create"))
async def create_payment(user_id: str, request: Request) -> dict[str, Any]:
    body = await request.json()
    plan_id = str(body.get("plan_id", "")).strip()
    amount = float(body.get("amount", 0))
    description = str(body.get("description", "Setka Plus")).strip()
    if not plan_id or amount <= 0:
        raise HTTPException(status_code=400, detail="Invalid plan")

    payment_id = f"pay-{uuid.uuid4().hex[:16]}"
    with db_conn() as conn:
        plan = conn.execute("SELECT * FROM plans WHERE id = ? AND active = 1", (plan_id,)).fetchone()
        if plan is None:
            raise HTTPException(status_code=404, detail="Plan not found")
        inv_id = next_inv_id(conn)
        conn.execute(
            """
            INSERT INTO payments (payment_id, inv_id, user_id, plan_id, amount, description, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)
            """,
            (payment_id, inv_id, user_id, plan_id, amount, description, utcnow().isoformat()),
        )

    checkout_url = build_checkout_url(
        inv_id=inv_id,
        amount=amount,
        description=description,
        payment_id=payment_id,
        user_id=user_id,
    )
    return {"payment_id": payment_id, "checkout_url": checkout_url}


@app.get(matrix_user_path("{user_id}", "/payments/{payment_id}"))
def get_payment_status(user_id: str, payment_id: str) -> dict[str, Any]:
    with db_conn() as conn:
        row = conn.execute(
            "SELECT * FROM payments WHERE payment_id = ? AND user_id = ?",
            (payment_id, user_id),
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="Payment not found")
        return {
            "payment_id": row["payment_id"],
            "status": row["status"],
            "plan_id": row["plan_id"],
            "subscription_active": subscription_active(conn, user_id),
            "message": row["message"],
        }


@app.api_route("/setka/robokassa/result", methods=["GET", "POST"])
async def robokassa_result(request: Request) -> PlainTextResponse:
    params = dict(request.query_params)
    if request.method == "POST":
        form = await request.form()
        params.update({k: str(v) for k, v in form.items()})

    out_sum = str(params.get("OutSum", ""))
    inv_id = str(params.get("InvId", ""))
    signature = str(params.get("SignatureValue", ""))
    shp = {k: str(v) for k, v in params.items() if k.lower().startswith("shp_")}

    if not out_sum or not inv_id or not signature:
        raise HTTPException(status_code=400, detail="Missing params")
    if not verify_result_signature(out_sum, inv_id, signature, shp):
        raise HTTPException(status_code=400, detail="Bad signature")

    with db_conn() as conn:
        payment = conn.execute("SELECT * FROM payments WHERE inv_id = ?", (int(inv_id),)).fetchone()
        if payment is None:
            raise HTTPException(status_code=404, detail="Payment not found")
        if payment["status"] == "paid":
            return PlainTextResponse(f"OK{inv_id}")

        plan = conn.execute("SELECT * FROM plans WHERE id = ?", (payment["plan_id"],)).fetchone()
        duration_days = int(plan["duration_days"]) if plan else 30
        conn.execute(
            "UPDATE payments SET status = 'paid', paid_at = ?, message = NULL WHERE payment_id = ?",
            (utcnow().isoformat(), payment["payment_id"]),
        )
        activate_subscription(conn, payment["user_id"], payment["plan_id"], duration_days)

    return PlainTextResponse(f"OK{inv_id}")


@app.get("/setka/robokassa/success")
def robokassa_success(request: Request) -> RedirectResponse:
    params = dict(request.query_params)
    out_sum = str(params.get("OutSum", ""))
    inv_id = str(params.get("InvId", ""))
    signature = str(params.get("SignatureValue", ""))
    shp = {k: str(v) for k, v in params.items() if k.lower().startswith("shp_")}

    payment_id = shp.get("Shp_payment_id", "")
    user_id = shp.get("Shp_user_id", "")

    if out_sum and inv_id and signature and not verify_success_signature(out_sum, inv_id, signature, shp):
        return RedirectResponse(build_app_redirect(user_id, payment_id, success=False))

    if not payment_id or not user_id:
        with db_conn() as conn:
            payment = conn.execute("SELECT * FROM payments WHERE inv_id = ?", (int(inv_id),)).fetchone()
            if payment:
                payment_id = payment["payment_id"]
                user_id = payment["user_id"]

    return RedirectResponse(build_app_redirect(user_id, payment_id, success=True))


@app.get("/setka/robokassa/fail")
def robokassa_fail(request: Request) -> RedirectResponse:
    params = dict(request.query_params)
    shp = {k: str(v) for k, v in params.items() if k.lower().startswith("shp_")}
    payment_id = shp.get("Shp_payment_id", "")
    user_id = shp.get("Shp_user_id", "")
    inv_id = params.get("InvId")
    if inv_id and (not payment_id or not user_id):
        with db_conn() as conn:
            payment = conn.execute("SELECT * FROM payments WHERE inv_id = ?", (int(inv_id),)).fetchone()
            if payment:
                payment_id = payment["payment_id"]
                user_id = payment["user_id"]
                conn.execute(
                    "UPDATE payments SET status = 'failed', message = ? WHERE payment_id = ?",
                    ("Оплата отменена", payment["payment_id"]),
                )
    return RedirectResponse(build_app_redirect(user_id, payment_id, success=False))


@app.get("/setka/robokassa/return/success")
def return_success(user_id: str, payment_id: str) -> HTMLResponse:
    deeplink = build_elementx_redirect(user_id, payment_id, success=True)
    html = f"""<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8"><title>Оплата прошла</title>
<meta http-equiv="refresh" content="0;url={deeplink}"></head>
<body><p>Оплата принята. Возвращаем в Setka…</p>
<p><a href="{deeplink}">Открыть приложение</a></p></body></html>"""
    return HTMLResponse(html)


@app.get("/setka/robokassa/return/fail")
def return_fail(user_id: str, payment_id: str) -> HTMLResponse:
    deeplink = build_elementx_redirect(user_id, payment_id, success=False)
    html = f"""<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8"><title>Оплата не прошла</title>
<meta http-equiv="refresh" content="0;url={deeplink}"></head>
<body><p>Оплата не завершена.</p>
<p><a href="{deeplink}">Вернуться в Setka</a></p></body></html>"""
    return HTMLResponse(html)

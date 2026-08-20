# Setka Plus vouchers

## Payment mode (Ketesa → Server Config)

`PUT /_synapse/admin/v1/setka_plus/server_config`

```json
{
  "config": {
    "payment_mode": "vouchers_only"
  }
}
```

Values:
- `acquiring_and_vouchers` — PayAnyWay + promo codes (default)
- `vouchers_only` — only promo codes; create payment returns 403

## Admin voucher CRUD

| Method | Path | Body |
|--------|------|------|
| GET | `/_synapse/admin/v1/setka_plus/vouchers` | — |
| POST | `/_synapse/admin/v1/setka_plus/vouchers` | see below |
| GET | `/_synapse/admin/v1/setka_plus/vouchers/{code}` | — |
| PUT | `/_synapse/admin/v1/setka_plus/vouchers/{code}` | partial update |
| DELETE | `/_synapse/admin/v1/setka_plus/vouchers/{code}` | soft-deactivate |
| GET | `/_synapse/admin/v1/setka_plus/vouchers/{code}/redemptions` | list users |

### Create voucher

```json
{
  "code": "SETKA-DEMO-2026",
  "plan_id": "month",
  "duration_days": 45,
  "expires_at": 1893456000000,
  "max_redemptions": 100,
  "max_per_user": 1,
  "note": "Launch promo"
}
```

- `code` optional — auto-generated if omitted
- **Required:** `plan_id` and/or `duration_days` (days override plan length on redeem)
- `expires_at` — Unix ms, must be in the future
- Stacking: redeem extends from current `expires_at` if Plus is already active

## Client API

`GET /_matrix/client/v3/user/{userId}/setka_plus/config` also returns `voucher_footer_text` (editable in Ketesa Server Config). Empty uses the default app string.

| Method | Path |
|--------|------|
| GET | `/_matrix/client/v3/user/{userId}/setka_plus/config` |
| GET | `/_matrix/client/v3/user/{userId}/setka_plus/plans` (includes checkout flags) |
| POST | `/_matrix/client/v3/user/{userId}/setka_plus/vouchers/redeem` `{ "code": "..." }` |

## Deploy

```bash
cp handlers/setka_plus.py /opt/venvs/matrix-synapse/lib/python3.13/site-packages/synapse/handlers/
cp rest/client/setka_plus.py .../synapse/rest/client/
cp rest/admin/setka_plus.py .../synapse/rest/admin/
systemctl restart matrix-synapse
```

## Ketesa UI checklist

1. Toggle on Server Config page: **Эквайринг+Ваучеры** / **Только ваучеры**
2. Vouchers tab: table + create form (code, plan, days, expiry, limits, note)
3. Voucher detail: edit, deactivate, redemption list

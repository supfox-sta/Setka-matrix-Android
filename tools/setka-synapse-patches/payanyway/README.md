# PayAnyWay — SELF (как Simpla 2)

См. **[SIMPLA_SELF.md](./SIMPLA_SELF.md)** — основная инструкция для вашего случая.

Пример модуля Simpla: [`simpla_reference/`](./simpla_reference/) (`Payanyway.php`, `callback.php`, `settings.xml`).

## Moneta.ru cabinet (SELF / Simpla)

| Setting | Value |
|---------|--------|
| Interface | MONETA.Assistant |
| **Check URL** | **пусто** |
| **Pay URL** | `https://matrix.setka-matrix.ru/setka/payanyway/pay` |
| Success / Fail URL | **пусто** |
| HTTP method | **POST** |
| Override URL in request | Yes |
| Form signature required | Yes |
| Integrity code | = `SETKA_PLUS_PAYANYWAY_INTEGRITY_CODE` |

## Environment variables

```bash
SETKA_PLUS_PAYANYWAY_MNT_ID=34838495
SETKA_PLUS_PAYANYWAY_INTEGRITY_CODE=148888
SETKA_PLUS_PAYANYWAY_IS_TEST=0
SETKA_PLUS_PAYANYWAY_PAYMENT_URL=https://www.payanyway.ru/assistant.htm
SETKA_PLUS_PAYANYWAY_CALLBACK_BASE_URL=https://matrix.setka-matrix.ru/setka/payanyway
SETKA_PLUS_PAYANYWAY_SIMPLA_SELF=1
SETKA_PLUS_PAYANYWAY_OVERRIDE_URLS=0
```

`checkout_url` из API ведёт на **POST-bridge** `/setka/payanyway/checkout?…`, не на GET Moneta.

## Synapse changes

1. Merge methods from `handlers/setka_plus_payanyway.py` into `synapse/handlers/setka_plus.py` (`SetkaPlusHandler`).
2. Add servlets from `rest/client/setka_plus_payanyway.py` to `synapse/rest/client/setka_plus.py` and register in `register_servlets`.
3. Mount public routes from `rest/synapse/client/setka_payanyway.py` (same pattern as `setka_robokassa.py`).

## Matrix API (Android client)

```
POST /_matrix/client/v3/user/{userId}/setka_plus/payments/payanyway/create
→ { "payment_id", "checkout_url", "amount", "currency", "plan_id", ... }

GET /_matrix/client/v3/user/{userId}/setka_plus/payments/{paymentId}
→ polling (unchanged)
```

Activation happens on **Pay URL** webhook only. Success/Fail URLs + App Links are UX hints.

## Signatures (reference)

**Checkout form:**

```
MD5(MNT_ID + MNT_TRANSACTION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE + MNT_SUBSCRIBER_ID + MNT_TEST_MODE + INTEGRITY_CODE)
```

**Pay URL notification:**

```
MD5(MNT_ID + MNT_TRANSACTION_ID + MNT_OPERATION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE + MNT_SUBSCRIBER_ID + MNT_TEST_MODE + INTEGRITY_CODE)
```

**Check URL:**

```
MD5(MNT_COMMAND + MNT_ID + MNT_TRANSACTION_ID + MNT_OPERATION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE + MNT_SUBSCRIBER_ID + MNT_TEST_MODE + INTEGRITY_CODE)
```

`MNT_AMOUNT` always two decimal places (e.g. `299.00`).

## Robokassa migration

- Android now calls `/payments/payanyway/create`.
- Keep Robokassa servlets until pending payments expire, or remove after cutover.
- Deeplinks accept both `/setka/payanyway/return/` and legacy `/setka/robokassa/return/`.

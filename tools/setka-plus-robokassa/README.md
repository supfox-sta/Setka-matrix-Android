# Setka Plus + Robokassa (reference server)

Эталонная реализация серверной части оплаты подписки Setka Plus через [Robokassa](https://docs.robokassa.ru/ru/).

**Важно:** подтверждение подписки выполняется **только** на `ResultURL` (Password #2).  
`SuccessURL` / `FailURL` — для UX и возврата в приложение, не для активации.

## Эндпоинты

| URL | Назначение |
|-----|------------|
| `POST /_matrix/client/v3/user/{userId}/setka_plus/payments/robokassa/create` | Создание платежа (Matrix CS API, с Bearer) |
| `GET /_matrix/client/v3/user/{userId}/setka_plus/payments/{payment_id}` | Статус платежа для polling в приложении |
| `GET/POST /setka/robokassa/result` | **ResultURL** — Robokassa → сервер, ответ `OK{InvId}` |
| `GET /setka/robokassa/success` | SuccessURL → проверка подписи (Password #1) → redirect в app |
| `GET /setka/robokassa/fail` | FailURL → redirect в app |
| `GET /setka/robokassa/return/success` | HTTPS deep link (Android App Link) |
| `GET /setka/robokassa/return/fail` | HTTPS deep link при ошибке |

## Переменные окружения

```bash
export ROBOKASSA_MERCHANT_LOGIN=your_shop
export ROBOKASSA_PASSWORD1=password_1
export ROBOKASSA_PASSWORD2=password_2
export ROBOKASSA_IS_TEST=1
export SETKA_PUBLIC_BASE_URL=https://matrix.setka-matrix.ru
export SETKA_DB_PATH=./setka_plus.db
```

## Запуск (локально)

```bash
cd tools/setka-plus-robokassa
pip install -r requirements.txt
uvicorn robokassa_server:app --host 0.0.0.0 --port 8080
```

## Интеграция с Synapse

1. Перенесите логику из `robokassa_server.py` в модуль homeserver (или reverse-proxy на этот сервис).
2. В Robokassa укажите:
   - **Result URL:** `https://matrix.setka-matrix.ru/setka/robokassa/result`
   - **Success URL:** `https://matrix.setka-matrix.ru/setka/robokassa/success`
   - **Fail URL:** `https://matrix.setka-matrix.ru/setka/robokassa/fail`
3. При создании платежа передавайте `Shp_payment_id` и `Shp_user_id` — они вернутся в callback.

## Android

После оплаты сервер редиректит на:

- `https://matrix.setka-matrix.ru/setka/robokassa/return/success?user_id=...&payment_id=...`

Приложение открывается по App Link, затем опрашивает  
`GET .../setka_plus/payments/{payment_id}` до `status=paid` и `subscription_active=true`.

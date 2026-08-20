# SELF PayAnyWay (как Simpla 2 + `kassa_pay_url`)

Схема **SELF** = ваш сервер принимает **Pay URL** (как `callback.php` в Simpla), а форма оплаты уходит **POST** на `www.payanyway.ru/assistant.htm`.

Это **не** kassa.payanyway.ru и **не** прямой GET на `assistant.moneta.ru`.

## Настройки счёта в Moneta / PayAnyWay (как в [инструкции Simpla](https://docs.moneta.ru/cms/simpla/index.html))

| Поле | Значение |
|------|----------|
| Check URL | **пусто** |
| Pay URL | `https://matrix.setka-matrix.ru/setka/payanyway/pay` |
| HTTP метод | **POST** |
| Можно переопределять URL | Да |
| Подпись формы оплаты | **Да** |
| Код проверки целостности | **должен совпадать символ в символ** с `SETKA_PLUS_PAYANYWAY_INTEGRITY_CODE` и `setka_server_config.json` → `payanyway_integrity_code` |
| Success / Fail / Return URL | **пусто** (как Simpla) |
| Тестовый режим | = `SETKA_PLUS_PAYANYWAY_IS_TEST` |

## systemd (Setka / Synapse)

```ini
Environment="SETKA_PLUS_PAYANYWAY_MNT_ID=34838495"
Environment="SETKA_PLUS_PAYANYWAY_INTEGRITY_CODE=148888"
Environment="SETKA_PLUS_PAYANYWAY_IS_TEST=0"
Environment="SETKA_PLUS_PAYANYWAY_PAYMENT_URL=https://www.payanyway.ru/assistant.htm"
Environment="SETKA_PLUS_PAYANYWAY_CALLBACK_BASE_URL=https://matrix.setka-matrix.ru/setka/payanyway"
Environment="SETKA_PLUS_PAYANYWAY_SIMPLA_SELF=1"
Environment="SETKA_PLUS_PAYANYWAY_OVERRIDE_URLS=0"
```

`API_USERNAME` / `API_PASSWORD` для Simpla — это **логин кабинета Moneta** (офлайн-оплаты), **не** для онлайн-формы Assistant. Для Setka Plus они не нужны при `SIMPLA_SELF=1`.

## Поток (аналог `Payanyway.php` + `callback.php`)

```
1. POST …/setka_plus/payments/payanyway/create
   → checkout_url = https://matrix…/setka/payanyway/checkout?…  (POST-bridge)

2. Браузер открывает checkout → auto-POST на www.payanyway.ru/assistant.htm
   (как <form method="post"> в Payanyway.php)

3. После оплаты Moneta POST на Pay URL → /setka/payanyway/pay
   → проверка подписи Pay-уведомления → ответ SUCCESS (как callback.php)

4. Пользователь возвращается в приложение (deeplink / polling статуса)
```

## Подписи

**Форма оплаты (Assistant):**

```
MD5(MNT_ID + MNT_TRANSACTION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE + MNT_SUBSCRIBER_ID + TEST_MODE + INTEGRITY)
```

`MNT_SUBSCRIBER_ID` не передаётся → пустая строка (как в Simpla без subscriber).

**Pay URL (callback.php):**

```
MD5(MNT_ID + MNT_TRANSACTION_ID + MNT_OPERATION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE + MNT_SUBSCRIBER_ID + TEST_MODE + INTEGRITY)
```

Ответ: plain text `SUCCESS` или `FAIL`, HTTP 200.

## Важно

- Не открывайте оплату GET-ссылкой на `assistant.moneta.ru/?MNT_…` — только POST-bridge.
- Код `148888` у вас **совпадает** с подписью в URL (`f483de4af…`). Если Moneta всё равно ругается — сверьте код **в кабинете** символ в символ.
- После правок: `systemctl restart matrix-synapse`, новый платёж в приложении.

# Setka Plus Synapse module

Drop-in модуль: **pip install + homeserver.yaml + restart**. Без миграций PostgreSQL — данные в JSON в `{data_dir}/setka_plus/`.

## Установка (3 шага)

### 1. Установить пакет на сервер Synapse

```bash
cd /path/to/element-x-android/tools/setka-plus-synapse
pip install .
# или в venv Synapse:
# /opt/venvs/matrix-synapse/bin/pip install .
```

### 2. Добавить в `homeserver.yaml`

```yaml
modules:
  - module: setka_plus_synapse_module.module.SetkaPlusModule
    config:
      robokassa_merchant_login: "your_shop_login"
      robokassa_password1: "password_1_from_robokassa"
      robokassa_password2: "password_2_from_robokassa"
      robokassa_is_test: true   # false на проде
      public_base_url: "https://matrix.setka-matrix.ru"
      plans:
        - id: setka_plus_month
          name: "Setka Plus — 30 дней"
          price_rub: 199
          duration_days: 30
          features:
            - "Стикеры и эмодзи"
            - "Обмен паками"
          active: true
          sort_order: 0
```

### 3. Перезапустить Synapse

```bash
systemctl restart matrix-synapse
```

После старта появится папка `{data_dir}/setka_plus/` с `payments.json` и `subscriptions.json` — **ничего создавать руками не нужно**.

---

## Robokassa (личный кабинет)

| Поле | Значение |
|------|----------|
| Result URL | `https://matrix.setka-matrix.ru/setka/robokassa/result` |
| Success URL | `https://matrix.setka-matrix.ru/setka/robokassa/success` |
| Fail URL | `https://matrix.setka-matrix.ru/setka/robokassa/fail` |

Подтверждение подписки — **только Result URL** ([документация](https://docs.robokassa.ru/ru/notifications-and-redirects)).

---

## Что делает модуль

| URL | Описание |
|-----|----------|
| `GET .../setka_plus/subscription` | Статус подписки |
| `GET .../setka_plus/plans` | Тарифы из yaml |
| `POST .../payments/robokassa/create` | Создать платёж + checkout URL |
| `GET .../payments/{payment_id}` | Статус для polling в Android |
| `/setka/robokassa/result` | Callback Robokassa → активация |
| `/setka/robokassa/return/success` | Deep link в приложение |

Matrix API регистрируется автоматически (без правки исходников Synapse).

---

## Если уже есть свой setka_plus API

Отключите дублирующие эндпоинты в старом коде или не подключайте этот модуль параллельно — будут конфликты маршрутов.

---

## Проверка

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://matrix.setka-matrix.ru/_matrix/client/v3/user/@user:matrix.setka-matrix.ru/setka_plus/plans"
```

После тестовой оплаты Result URL должен вернуть `OK123`, а status endpoint — `"status": "paid", "subscription_active": true`.

---

## Если Synapse не стартует после добавления модуля

### 1. Получить полный текст ошибки

```bash
sudo journalctl -u matrix-synapse -n 80 --no-pager
```

Или запустить вручную (увидите ошибку в терминале):

```bash
sudo -u matrix-synapse /opt/venvs/matrix-synapse/bin/python -m synapse.app.homeserver \
  --config-path /etc/matrix-synapse/homeserver.yaml
```

### 2. Частые причины

| Симптом | Решение |
|---------|---------|
| `ModuleNotFoundError: setka_plus_synapse_module` | Модуль не установлен в **тот же venv**, что Synapse: `/opt/venvs/matrix-synapse/bin/pip install .` |
| `setka_plus.public_base_url is required` | Добавьте `public_base_url: "https://matrix.setka-matrix.ru"` в `config:` модуля |
| `robokassa_merchant_login is required` | Заполните все поля Robokassa в `config:` |
| `plans must be a non-empty list` | Добавьте хотя бы один тариф в `plans:` |
| YAML syntax error | Проверьте отступы: `config:` и `plans:` с двумя пробелами глубже, чем `- module:` |

### 3. Сообщения, которые **не** ломают старт

- `Config is missing macaroon_secret_key` — Synapse сам сгенерирует ключ при первом успешном запуске (или добавьте в yaml вручную).
- `trusted_key_servers` / `matrix.org` — предупреждение; можно добавить `suppress_key_server_warning: true`.

### 4. Проверить, что модуль виден Python

```bash
cd /
/opt/venvs/matrix-synapse/bin/python -c "from setka_plus_synapse_module.module import SetkaPlusModule; print('OK')"
```

Должно напечатать `OK`. Запускайте **не из папки исходников** (`~/setka-plus-synapse`), иначе Python может подхватить локальную копию вместо установленной.

После правок переустановите модуль:

```bash
cd ~/setka-plus-synapse
/opt/venvs/matrix-synapse/bin/pip install --force-reinstall .
```

### 5. Временно отключить модуль

Закомментируйте блок `- module: setka_plus_synapse_module...` в `homeserver.yaml` и перезапустите — Synapse должен снова подняться. Затем исправьте config и включите модуль обратно.

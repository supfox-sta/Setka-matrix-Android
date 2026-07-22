# Setka Plus — REST-патч Synapse (без modules)

Один файл `setka_plus.py` + конфиг `setka_plus.yaml` + **2 строки** в Synapse.

## 1. Удалить module (если ставили)

В `/etc/matrix-synapse/homeserver.yaml` **удалите или закомментируйте**:

```yaml
modules:
  - module: setka_plus_synapse_module.module.SetkaPlusModule
    ...
```

И при желании: `pip uninstall setka-plus-synapse-module`

---

## 2. Скопировать файлы

```bash
SITE=/opt/venvs/matrix-synapse/lib/python3.13/site-packages

cp setka_plus.py "$SITE/synapse/rest/client/setka_plus.py"
cp setka_plus.yaml.example /etc/matrix-synapse/setka_plus.yaml
nano /etc/matrix-synapse/setka_plus.yaml   # Robokassa + тарифы
```

---

## 3. Патч `synapse/rest/__init__.py`

**A)** В блок импортов `from synapse.rest.client import (` добавьте:

```python
    setka_plus,
```

**B)** В кортеж `CLIENT_SERVLET_FUNCTIONS` добавьте **в конец**:

```python
    setka_plus.register_servlets,
```

Пример фрагмента:

```python
from synapse.rest.client import (
    ...
    thread_subscriptions,
    setka_plus,
)

CLIENT_SERVLET_FUNCTIONS: tuple[RegisterServletsFunc, ...] = (
    ...
    thread_subscriptions.register_servlets,
    setka_plus.register_servlets,
)
```

---

## 4. Патч `synapse/app/homeserver.py`

**A)** В импорты (рядом с `from synapse.rest import ClientRestResource`):

```python
from synapse.rest.client import setka_plus
```

**B)** В методе `_listener_http`, **перед** строкой:

```python
        resources.update(self._module_web_resources)
```

добавьте:

```python
        resources.update(setka_plus.build_robokassa_resources(self))
```

---

## 5. Перезапуск

```bash
systemctl restart matrix-synapse
journalctl -u matrix-synapse -n 50 --no-pager | grep -i setka
```

Ожидается:

```
Setka Plus config loaded from /etc/matrix-synapse/setka_plus.yaml
Setka Plus Matrix REST servlets registered
Setka Plus store at /var/lib/matrix-synapse/setka_plus
```

---

## API (как в Android)

| Метод | URL |
|-------|-----|
| GET | `/_matrix/client/v3/user/{userId}/setka_plus/subscription` |
| GET | `/_matrix/client/v3/user/{userId}/setka_plus/plans` |
| POST | `/_matrix/client/v3/user/{userId}/setka_plus/payments/robokassa/create` |
| GET | `/_matrix/client/v3/user/{userId}/setka_plus/payments/{paymentId}` |

Robokassa:

| URL |
|-----|
| `/setka/robokassa/result` |
| `/setka/robokassa/success` |
| `/setka/robokassa/fail` |

Данные: `{data_dir}/setka_plus/*.json` (создаётся само).

---

## Setka channels (комментарии 403 + каталог каналов)

Тот же способ, **без modules** — файл `setka_channels.py`.

### Скопировать

```bash
SITE=/opt/venvs/matrix-synapse/lib/python3.13/site-packages
cp setka_channels.py "$SITE/synapse/rest/client/setka_channels.py"
```

### Патч `synapse/rest/__init__.py`

Добавьте импорт `setka_channels` и в конец `CLIENT_SERVLET_FUNCTIONS`:

```python
    setka_channels.register_servlets,
```

### Патч `synapse/app/homeserver.py`

```python
from synapse.rest.client import setka_channels
```

В `HomeServer.setup()` (один раз при старте):

```python
        setka_channels.install(self)
```

Перезапуск Synapse. В логах:

```
Setka channels event rules installed (topic prefix=setka:channel)
Setka channels Matrix REST servlets registered
```

### API

| Метод | URL |
|-------|-----|
| GET | `/_matrix/client/v3/setka/channels/public?limit=40&since=…` |

Ответ: `{ "channels": [ { "room_id", "name", "topic", "avatar_url", "num_joined_members", ... } ], "next_batch" }`

Правила отправки (комментарии в треде при `events_default=50`) включаются через **`install(self)`**, не через REST-тело запроса.

Подробнее: `docs/setka-lite/setka-channels-synapse.md`

---

## Тесты

### На сервере (Synapse venv)

```bash
cd ~/setka-plus-synapse-rest
/opt/venvs/matrix-synapse/bin/python test_setka_plus.py
```

### Smoke-тест API (нужен access token)

В Element: Настройки → Помощь и о программе → long tap на версию → скопировать access token.

```bash
export BASE_URL="https://matrix.setka-matrix.ru"
export USER_ID="@YOU:matrix.setka-matrix.ru"
export ACCESS_TOKEN="syt_..."
# опционально — полная симуляция ResultURL:
export ROBOKASSA_PASSWORD2="..."   # password #2 из setka_plus.yaml

chmod +x test_smoke.sh
./test_smoke.sh
```

### Android (локально)

```bash
./gradlew :libraries:deeplink:impl:testDebugUnitTest \
  --tests "io.element.android.libraries.deeplink.impl.DefaultDeeplinkParserTest"
./gradlew :libraries:matrix:api:testDebugUnitTest \
  --tests "io.element.android.libraries.matrix.api.setka.SetkaPlusModelsTest"
```

### Если `/setka/robokassa/*` → 404

Matrix API (`/_matrix/client/v3/.../setka_plus/...`) работает, но Robokassa — нет:  
Apache/nginx не проксирует `/setka/` на Synapse. Добавьте в vhost:

```apache
ProxyPass /setka/ http://127.0.0.1:8008/setka/
ProxyPassReverse /setka/ http://127.0.0.1:8008/setka/
```

(порт — ваш listener Synapse)

Конфиг: `/etc/matrix-synapse/setka_plus.yaml`  
Переопределить путь: env `SETKA_PLUS_CONFIG=/path/to.yaml`

---

## Проверка

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://matrix.setka-matrix.ru/_matrix/client/v3/user/@USER:matrix.setka-matrix.ru/setka_plus/plans"
```

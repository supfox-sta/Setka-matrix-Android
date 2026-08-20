# Setka login verification & S/Key recovery on Synapse

Copy:

- `handlers/setka_login.py` → `synapse/handlers/setka_login.py`
- `rest/client/setka_login.py` → `synapse/rest/client/setka_login.py`

## Register servlets

In `synapse/rest/__init__.py`:

```python
from synapse.rest.client import setka_login

CLIENT_SERVLET_FUNCTIONS = (
    ...
    setka_login.register_servlets,
)
```

In `synapse/server.py` (or `app/homeserver.py`):

```python
from synapse.handlers import setka_login as setka_login_handler

# in HomeServer class:
def get_setka_login_handler(self) -> setka_login_handler.SetkaLoginHandler:
    return self._setka_login_handler

# in setup():
self._setka_login_handler = setka_login_handler.SetkaLoginHandler(self)
```

## S/Key toggle (store / delete / re-store)

| Endpoint | Purpose |
|----------|---------|
| `POST /_matrix/client/v3/setka/login/recovery/store` | Save encrypted recovery key (no login verification required) |
| `POST /_matrix/client/v3/setka/login/recovery/fetch` | Fetch key; marks device verified |
| `POST /_matrix/client/v3/setka/login/recovery/delete` | Remove server backup only; user can store again later |

After `delete`, `store_recovery_key` accepts a new key — there is no permanent disable flag.

Optional env: `SETKA_LOGIN_RECOVERY_SECRET` (falls back to `registration_shared_secret`, then `macaroon_secret_key`).

Restart Synapse after copying files.

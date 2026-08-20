# Setka privacy on Synapse

Copy:

- `handlers/setka_privacy.py` → `synapse/handlers/setka_privacy.py`
- `rest/client/setka_privacy.py` → `synapse/rest/client/setka_privacy.py`

## Register servlets

In `synapse/rest/__init__.py`:

```python
from synapse.rest.client import setka_privacy

CLIENT_SERVLET_FUNCTIONS = (
    ...
    setka_privacy.register_servlets,
)
```

In `synapse/server.py`:

```python
from synapse.handlers import setka_privacy as setka_privacy_handler

# in HomeServer class:
def get_setka_privacy_handler(self) -> setka_privacy_handler.SetkaPrivacyHandler:
    return self._setka_privacy_handler

# in setup():
self._setka_privacy_handler = setka_privacy_handler.SetkaPrivacyHandler(self)
```

## REST

`GET /_matrix/client/v3/user/{userId}/setka_privacy`

`PUT /_matrix/client/v3/user/{userId}/setka_privacy`

Fields include `call_visibility`: `everyone` | `contacts` | `nobody`.

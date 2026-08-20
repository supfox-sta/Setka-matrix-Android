# Setka channels on Synapse

Copy:

- `handlers/setka_channels.py` → `synapse/handlers/setka_channels.py`
- `rest/client/setka_channels.py` → `synapse/rest/client/setka_channels.py`

**Also deploy the [feed patch](../feed/README.md)** — publish and public listing depend on `SetkaFeedHandler`.

## Register servlets

In `synapse/rest/__init__.py`:

```python
from synapse.rest.client import setka_channels

CLIENT_SERVLET_FUNCTIONS = (
    ...
    setka_channels.register_servlets,
)
```

In `synapse/server.py` (or `app/homeserver.py`):

```python
from synapse.handlers import setka_channels as setka_channels_handler

# in HomeServer class:
def get_setka_channels_handler(self) -> setka_channels_handler.SetkaChannelsHandler:
    return self._setka_channels_handler

# in setup():
self._setka_channels_handler = setka_channels_handler.SetkaChannelsHandler(self)
```

## Event auth (thread comments)

Patch `synapse/event_auth.py` — call `setka_channel_thread_comment_send_level` from `setka_channels` handler module.

## REST

`GET /_matrix/client/v3/setka/channels/public?limit=40&since=…&q=news`

Returns public rooms whose topic starts with `setka:channel`. Optional `q` matches name, topic, alias, `#tag`.

## Channel topic format (Android)

```
setka:channel #mytag Optional description
```

Public join + shared history required for discovery.

## Publish

`POST /_matrix/client/v3/setka/channels/{roomId}/publish`

Body for **public** channels (required):

```json
{"category_id": "humor"}
```

Category ids: `humor`, `creativity`, `news`, `tech`, `music`, `education`, `lifestyle`, `games`.

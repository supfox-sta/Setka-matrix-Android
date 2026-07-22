# Setka channels on Synapse (REST patch, no modules)

The Android client marks channels with `m.room.topic` prefix `setka:channel` (see `SetkaChannelConfig`).

## Server setup (same style as Setka Plus REST)

Copy `tools/setka-plus-synapse-rest/setka_channels.py` to  
`synapse/rest/client/setka_channels.py` in the Synapse venv.

### Patch `synapse/rest/__init__.py`

```python
from synapse.rest.client import (
    ...
    setka_plus,
    setka_channels,
)

CLIENT_SERVLET_FUNCTIONS: tuple[RegisterServletsFunc, ...] = (
    ...
    setka_plus.register_servlets,
    setka_channels.register_servlets,
)
```

### Patch `synapse/app/homeserver.py`

Import:

```python
from synapse.rest.client import setka_channels
```

In `HomeServer.setup()` (after the homeserver is constructed, before serving), **once**:

```python
        setka_channels.install(self)
```

Place near other Setka hooks (e.g. same area as `setka_plus.build_robokassa_resources` registration).  
Do **not** add anything under `modules:` in `homeserver.yaml`.

Restart Synapse. Logs should include:

- `Setka channels event rules installed`
- `Setka channels Matrix REST servlets registered`

## Behaviour

1. **Event rules** — main feed: only admin / moderator (PL ≥ 50) may send plain `m.room.message` / `m.sticker` (`check_event_allowed`). **Comment threads** (`m.relates_to` / reply shapes) are allowed for all members. Event content in Synapse is **`immutabledict`**, not `dict` — handlers must use `collections.abc.Mapping`, or thread detection silently fails and subscribers stay blocked.  
   - **`event_auth` patch** (`setka_channel_thread_comment_send_level`): if the room still has `events_default = 50`, members can send threaded comments (send level falls back to `users_default`).  
   - **Android** sends thread comments via `sendReply` to the post root so events are clearly threaded on the server.

2. **REST** — `GET /_matrix/client/v3/setka/channels/public?limit=40&since=…`  
   Returns `{ "channels": [...], "next_batch": "..." }` for public rooms whose topic starts with `setka:channel`.

3. **On create (client)** — sets `history_visibility: shared` and topic marker. **Do not** raise `events_default` to 50 (that blocked thread auth); moderation is server-side only.

4. **`events/validator.py`** — `unfreeze()` `m.mentions` before Pydantic (frozen event content turns `user_ids` lists into tuples → `M_BAD_JSON`).

5. **`handlers/message.py`** — allow `m.thread` when the parent is already part of a thread (reply to a comment), not only when the parent is the root post.

Integrated tree: `synapse/handlers/setka_channels.py`, `synapse/rest/client/setka_channels.py`, patches in `server.py`, `rest/__init__.py`, `event_auth.py`, `events/validator.py`, `handlers/message.py`.

## Optional: remove failed module install

If you tried `tools/setka-channels-synapse` as a yaml module, remove it from `homeserver.yaml` and use this REST patch instead.

Full copy steps: `tools/setka-plus-synapse-rest/README.md` (Setka channels section).

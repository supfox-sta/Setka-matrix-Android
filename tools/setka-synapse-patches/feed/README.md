# Setka feed on Synapse

Required for `/setka/feed/categories`, subscriptions, and feed posts.

## Copy files

- `handlers/setka_feed.py` → `synapse/handlers/setka_feed.py`
- `rest/client/setka_feed.py` → `synapse/rest/client/setka_feed.py`

Also deploy the updated **channels** patch (publish now requires `category_id` for public rooms).

## Register handler (`synapse/server.py`)

```python
from synapse.handlers.setka_feed import SetkaFeedHandler

# in HomeServer:
@cache_in_self
def get_setka_feed_handler(self) -> SetkaFeedHandler:
    return SetkaFeedHandler(self)
```

## Register servlets

In `synapse/rest/client/__init__.py`:

```python
"setka_feed",
```

In `synapse/rest/__init__.py`:

```python
from synapse.rest.client import setka_feed

CLIENT_SERVLET_FUNCTIONS = (
    ...
    setka_feed.register_servlets,
)
```

## Restart

```bash
systemctl restart matrix-synapse
journalctl -u matrix-synapse -n 100 --no-pager | grep -i setka
```

## Verify

```bash
curl -s 'https://YOUR_HS/_matrix/client/v3/setka/feed/categories' \
  -H 'Authorization: Bearer YOUR_TOKEN'
```

Expected: `{"categories":[{"id":"humor","label":"#Юмор",...},...]}`

Publish (public channel, category required):

```bash
curl -s -X POST 'https://YOUR_HS/_matrix/client/v3/setka/channels/!ROOM%3Aserver/publish' \
  -H 'Authorization: Bearer YOUR_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"category_id":"humor"}'
```

## Optional PostgreSQL migration

Tables are created automatically on first request. Runtime migrations use `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` on PostgreSQL (and `PRAGMA table_info` checks on SQLite), so re-running schema setup no longer aborts the transaction with `InFailedSqlTransaction`.

If an old partial deploy left broken tables, run manually:

```sql
CREATE TABLE IF NOT EXISTS setka_channel_categories (
    id TEXT NOT NULL PRIMARY KEY,
    label TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE setka_published_channels ADD COLUMN IF NOT EXISTS hidden INTEGER NOT NULL DEFAULT 0;
ALTER TABLE setka_published_channels ADD COLUMN IF NOT EXISTS category_id TEXT;
ALTER TABLE setka_published_channels ADD COLUMN IF NOT EXISTS feed_enabled INTEGER NOT NULL DEFAULT 1;
```

Then restart Synapse and hit `/setka/feed/categories` once (seeds default categories).

## Common 500 causes

1. **Feed files not deployed** — route exists but handler missing or old code with `ON CONFLICT` on tables without PRIMARY KEY.
2. **`get_setka_feed_handler` missing** in `server.py` — Synapse may fail to start or return 500 on publish/categories.
3. **Publish without `category_id`** on a public channel — should return 400, not 500 (after channels patch is deployed).
4. **PostgreSQL migration bug (fixed)** — old handlers used `try/except` around `ALTER TABLE ADD COLUMN`; on PostgreSQL a duplicate column error aborts the whole transaction and subsequent statements fail with `InFailedSqlTransaction`. Deploy the updated `setka_feed.py` and `setka_channels.py` from this patch.

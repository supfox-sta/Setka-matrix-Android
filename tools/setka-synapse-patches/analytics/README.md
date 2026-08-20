# Setka Analytics (PostHog + server BI)

Investor-grade metrics: DAU/WAU/MAU, retention, Setka Plus, S/Key, channels.

## 1. Database

```bash
psql -d synapse -f schema/setka_analytics.sql
```

## 2. Copy files

| Patch | Target |
|-------|--------|
| `handlers/setka_analytics.py` | `synapse/handlers/setka_analytics.py` |
| `rest/client/setka_analytics.py` | `synapse/rest/client/setka_analytics.py` |
| `rest/admin/setka_analytics.py` | `synapse/rest/admin/setka_analytics.py` |

## 3. Wire Synapse

**`synapse/server.py`** — in `HomeServer` class:

```python
def get_setka_analytics_handler(self) -> SetkaAnalyticsHandler:
    return self.get_datastores().main.get_setka_analytics_handler()
```

**`synapse/app/homeserver.py`** (or wherever handlers are constructed):

```python
from synapse.handlers.setka_analytics import SetkaAnalyticsHandler
# in setup(): self.setka_analytics_handler = SetkaAnalyticsHandler(self)
```

**`synapse/rest/client/__init__.py`**:

```python
from synapse.rest.client.setka_analytics import register_servlets as setka_analytics_register
# in register_servlets: setka_analytics_register(hs, http_server)
```

**`synapse/rest/admin/__init__.py`**:

```python
from synapse.rest.admin.setka_analytics import register_servlets as setka_analytics_admin_register
# in register_servlets: setka_analytics_admin_register(hs, http_server)
```

## 4. Server-side event hooks (recommended)

In **`setka_login.py`** after successful verify / fallback / recovery store:

```python
await self._hs.get_setka_analytics_handler().record_event(
    event_name="login.verified",  # or skey.enabled, login.fallback.emoji, etc.
    user_id=user_id,
    platform="server",
    source="server",
)
```

In **`setka_plus.py`** on payment paid / subscription activated:

```python
event_name="plus.payment_paid"  # plus.subscription_activated
```

In **`setka_channels.py`** on publish:

```python
event_name="channel.published"
```

See `hooks/setka_analytics_hooks.example.py` for copy-paste snippets.

## 5. API

### Client (authenticated)

`POST /_matrix/client/v3/setka/analytics/capture`

```json
{
  "platform": "web",
  "app_version": "1.0.0",
  "events": [
    { "event_name": "screen.viewed", "properties": { "screen": "SetkaChannelDiscovery" } }
  ]
}
```

Also records deduplicated `session.active` per request.

### Admin (investor dashboard)

- `GET /_synapse/admin/v1/setka/analytics/overview?days=30`
- `GET /_synapse/admin/v1/setka/analytics/retention?days=30`

## 6. PostHog (self-hosted)

Deploy PostHog at `https://analytics.setka-matrix.ru` (see `POSTHOG.md`).

Clients send UX funnels to PostHog; critical events duplicate to Synapse via `/setka/analytics/capture`.

| Layer | Purpose |
|-------|---------|
| **PostHog** | Funnels, session replay, feature flags, investor export |
| **Synapse table** | Source of truth for DAU/Plus/S/Key (server + client batch) |

## Event catalog

| Event | Source |
|-------|--------|
| `session.active` | Client capture + daily dedupe |
| `user.registered` | Server registration |
| `login.verified` | Server login handler |
| `login.skey.silent_success` | Web/Android silent S/Key |
| `skey.enabled` / `skey.disabled` | Server recovery store/delete |
| `plus.payment_paid` | Server payment webhook |
| `plus.subscription_activated` | Server Plus handler |
| `channel.published` | Server channels |
| `screen.viewed` | Client PostHog + capture batch |

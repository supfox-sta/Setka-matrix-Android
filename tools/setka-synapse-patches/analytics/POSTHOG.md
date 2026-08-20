# PostHog self-host for Setka Matrix

Investor-ready product analytics on your own infrastructure.

## Recommended stack

```
analytics.setka-matrix.ru  →  PostHog (Docker Compose)
matrix.setka-matrix.ru     →  Synapse /setka/analytics/* (source of truth BI)
admin.setka-matrix.ru      →  S/Matrix Console dashboard
```

## Quick deploy (Docker Compose)

```yaml
# docker-compose.analytics.yml
services:
  posthog:
    image: posthog/posthog:latest
    ports:
      - "8000:8000"
    environment:
      SECRET_KEY: "<generate-long-random>"
      SITE_URL: https://analytics.setka-matrix.ru
      DATABASE_URL: postgres://posthog:posthog@db:5432/posthog
      REDIS_URL: redis://redis:6379
    depends_on:
      - db
      - redis
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: posthog
      POSTGRES_PASSWORD: posthog
      POSTGRES_DB: posthog
  redis:
    image: redis:7-alpine
```

Put nginx TLS in front; create project in PostHog UI; copy **Project API Key** (`phc_...`).

## Client configuration

### Web (`config.setka.json`)

```json
"posthog": {
  "project_api_key": "phc_YOUR_KEY",
  "api_host": "https://analytics.setka-matrix.ru"
},
"setka_analytics": {
  "enabled": true,
  "server_sync": true
}
```

### Android (`BuildTimeConfig.kt`)

```kotlin
const val SETKA_POSTHOG_HOST = "https://analytics.setka-matrix.ru"
const val SETKA_POSTHOG_APIKEY = "phc_YOUR_KEY"
```

## Investor metrics checklist

| Metric | PostHog | Synapse admin API |
|--------|---------|-------------------|
| DAU / WAU / MAU | Trends | `/setka/analytics/overview` |
| Retention D1/D7/D30 | Retention insight | `/setka/analytics/retention` |
| Setka Plus revenue | Custom events + Stripe | `plus.payment_paid` count |
| S/Key adoption | Funnel | `skey.enabled` / total users |
| Platform split | Breakdown by `$os` | `platform_split_30d` |
| Channel growth | `channel.published` | overview |

## Privacy

- PostHog: pseudonymous `distinct_id` (Matrix analytics ID), no message content
- Synapse: operational events only; hash user_id in exports for external decks if needed
- Enable Element opt-in toast on web; Android analytics settings unchanged

## Grafana / export

PostHog → CSV/JSON export for pitch decks.  
Synapse admin dashboard (S/Matrix Console) → live KPI wall for ops.

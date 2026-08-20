# Setka Analytics — Architecture

Investor-ready product analytics for Setka Matrix (Web + Android + Synapse).

## Layers

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  Web / Android  │────▶│  PostHog (self-host) │────▶│  Funnels, exports   │
│  SetkaAnalytics │     │  analytics.setka-*   │     │  for pitch decks    │
└────────┬────────┘     └──────────────────────┘     └─────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Synapse  POST /setka/analytics/capture  →  setka_analytics_events (PG) │
│  Admin    GET  /setka/analytics/overview  →  S/Matrix Console dashboard │
└─────────────────────────────────────────────────────────────────────────┘
```

## Deploy checklist

1. **PostgreSQL** — `tools/setka-synapse-patches/analytics/schema/setka_analytics.sql`
2. **Synapse patch** — copy handlers + REST, wire `server.py` (see `analytics/README.md`)
3. **PostHog** — Docker on `analytics.setka-matrix.ru` (see `analytics/POSTHOG.md`)
4. **Web** — replace `phc_REPLACE_AFTER_POSTHOG_DEPLOY` in `config.setka.json`
5. **Android** — replace key in `BuildTimeConfig.SETKA_POSTHOG_APIKEY`
6. **Admin** — Ketesa route `/setka/analytics` (S/Matrix Console)

## Investor KPIs (dashboard)

| Metric | Source |
|--------|--------|
| DAU / WAU / MAU | Server event log |
| Retention D1/D7/D30 | Server SQL cohort |
| Total users | Synapse `users` table |
| Setka Plus payments | `plus.payment_paid` events |
| S/Key adoption | `skey.enabled` / users |
| Channel growth | `channel.published` |
| Platform split | `platform` dimension on events |

## Event catalog

See `tools/setka-synapse-patches/analytics/handlers/setka_analytics.py` → `ALLOWED_EVENTS`.

## Privacy

- No message content, room names, or PII in analytics properties
- PostHog: pseudonymous ID (Element analytics account data on web)
- Server events: operational facts only

## Next steps (admin console redesign)

- Split login screen (SSO + gradient)
- Collapsible sidebar categories
- Default landing → `/setka/analytics`

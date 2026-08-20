# Setka Synapse patches

Copy files from this tree into your Synapse venv (`site-packages/synapse/`) and restart homeserver.

## Modules

| Directory | Purpose |
|-----------|---------|
| `channels/` | Setka broadcast channels (REST discovery, thread comments) |
| `privacy/` | Setka user privacy settings (`setka_privacy`, call visibility) |
| `login/` | Setka login verification + S/Key recovery backup (store/fetch/delete) |
| `analytics/` | Business analytics (PostHog sync + investor KPI admin API) |
| `payanyway/` | Setka Plus payments via PayAnyWay (MONETA.Assistant) |

See each subdirectory `README.md` for wiring (`rest/__init__.py`, `server.py`, public routes).

## Android client expectations

- Channels: `GET /_matrix/client/v3/setka/channels/public?limit=&since=&q=`
- S/Key: `POST /_matrix/client/v3/setka/login/recovery/{store,fetch,delete}` (re-enable after delete via `store`)
- Plus: `POST /_matrix/client/v3/user/{userId}/setka_plus/payments/payanyway/create`
- Pay URL (Moneta → Synapse): `https://matrix.setka-matrix.ru/setka/payanyway/pay`
- Check URL: `https://matrix.setka-matrix.ru/setka/payanyway/check`
- Success/Fail return: `/setka/payanyway/return/success|fail`

## Web client CORS (web-beta / web on another origin)

If the Element Web app is served from a **different origin** than the homeserver (e.g. `https://web-beta.setka-matrix.ru` → `https://matrix.setka-matrix.ru`), the browser requires CORS headers on Matrix Client-Server API responses (including `/sync`).

Symptoms without CORS:

- Console: `blocked by CORS policy: No 'Access-Control-Allow-Origin'`
- Sync 401/503, client reload loops (fixed in web `SetkaSessionGuard` — no reload on auth/CORS errors)

**Fix on reverse proxy (nginx example for `matrix.setka-matrix.ru`):**

```nginx
location /_matrix/ {
    # Pass through to Synapse; ensure OPTIONS preflight succeeds
    if ($request_method = OPTIONS) {
        add_header Access-Control-Allow-Origin $http_origin always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Authorization, Content-Type, X-Requested-With" always;
        add_header Access-Control-Max-Age 86400 always;
        return 204;
    }
    proxy_pass http://127.0.0.1:8008;
    proxy_set_header Host $host;
    # Synapse also sets Access-Control-Allow-Origin: * on many routes;
    # if headers are stripped, add:
    add_header Access-Control-Allow-Origin $http_origin always;
}
```

Allowed origins should include at least:

- `https://web.setka-matrix.ru`
- `https://web-beta.setka-matrix.ru`

Alternative: serve the web app **same-origin** with the HS (e.g. `https://matrix.setka-matrix.ru/webapp/`) to avoid cross-origin sync entirely.

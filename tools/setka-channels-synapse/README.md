# Deprecated — use REST patch instead

Synapse **modules** (`homeserver.yaml` → `modules:`) did not work in our deployments.

Use **`tools/setka-plus-synapse-rest/setka_channels.py`**:

- `setka_channels.register_servlets` in `synapse/rest/__init__.py`
- `setka_channels.install(hs)` in `synapse/app/homeserver.py`

See `tools/setka-plus-synapse-rest/README.md` and `docs/setka-lite/setka-channels-synapse.md`.

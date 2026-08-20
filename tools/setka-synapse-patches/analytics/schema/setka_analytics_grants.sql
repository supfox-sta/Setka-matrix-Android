-- Run after setka_analytics.sql if analytics_ready is false in admin API.
-- Tables created as postgres must be readable/writable by Synapse DB user.
--
--   grep user: /etc/matrix-synapse/homeserver.yaml
--   sudo -u postgres psql -d synapse -f setka_analytics_grants.sql
--
-- Edit the role name below if your database.user is not `synapse`.

GRANT SELECT, INSERT, UPDATE, DELETE ON setka_analytics_events TO synapse;
GRANT SELECT, INSERT, UPDATE, DELETE ON setka_analytics_daily TO synapse;
GRANT USAGE, SELECT ON SEQUENCE setka_analytics_events_id_seq TO synapse;

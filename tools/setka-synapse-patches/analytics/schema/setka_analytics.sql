-- Setka business analytics (run once against Synapse PostgreSQL)
-- psql -d synapse -f setka_analytics.sql

CREATE TABLE IF NOT EXISTS setka_analytics_events (
    id          BIGSERIAL PRIMARY KEY,
    event_name  TEXT NOT NULL,
    user_id     TEXT,
    platform    TEXT,
    app_version TEXT,
    device_id   TEXT,
    ts          BIGINT NOT NULL,
    properties  JSONB NOT NULL DEFAULT '{}'::jsonb,
    source      TEXT NOT NULL DEFAULT 'client'
);

CREATE INDEX IF NOT EXISTS setka_analytics_events_ts_idx
    ON setka_analytics_events (ts DESC);

CREATE INDEX IF NOT EXISTS setka_analytics_events_name_ts_idx
    ON setka_analytics_events (event_name, ts DESC);

CREATE INDEX IF NOT EXISTS setka_analytics_events_user_ts_idx
    ON setka_analytics_events (user_id, ts DESC)
    WHERE user_id IS NOT NULL;

-- Daily active users (materialized; refresh via admin job or cron)
CREATE TABLE IF NOT EXISTS setka_analytics_daily (
    day         DATE NOT NULL,
    metric      TEXT NOT NULL,
    value       BIGINT NOT NULL DEFAULT 0,
    dimensions  JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (day, metric, dimensions)
);

-- Synapse connects as a non-superuser (see database.user in homeserver.yaml).
-- Tables created via `sudo -u postgres psql` are owned by postgres — grant access:
--   \du                          -- list roles
--   \connect synapse
-- Replace `synapse` below with your homeserver.yaml database.user if different.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synapse') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON setka_analytics_events TO synapse;
        GRANT SELECT, INSERT, UPDATE, DELETE ON setka_analytics_daily TO synapse;
        GRANT USAGE, SELECT ON SEQUENCE setka_analytics_events_id_seq TO synapse;
    END IF;
END $$;

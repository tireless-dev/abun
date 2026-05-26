CREATE TABLE IF NOT EXISTS preference (
    pref_key text PRIMARY KEY,
    pref_value text,
    value_type text NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    hlc_map jsonb NOT NULL DEFAULT '{}'::jsonb,
    server_version bigint NOT NULL,
    server_updated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_preference_server_version
ON preference(server_version);

CREATE TRIGGER trg_preference_sync_metadata
BEFORE INSERT OR UPDATE ON preference
FOR EACH ROW
EXECUTE FUNCTION assign_sync_metadata();

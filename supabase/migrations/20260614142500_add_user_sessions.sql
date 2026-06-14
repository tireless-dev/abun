CREATE TABLE IF NOT EXISTS user_session (
    id text PRIMARY KEY,
    user_id text NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    refresh_token_hash text NOT NULL,
    refresh_expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    rotated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_session_user_id_idx
    ON user_session(user_id);

CREATE INDEX IF NOT EXISTS user_session_refresh_expires_at_idx
    ON user_session(refresh_expires_at);

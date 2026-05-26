CREATE TABLE IF NOT EXISTS pomodoro_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    task_id uuid,
    phase text NOT NULL,
    state text NOT NULL,
    started_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    completed_at timestamptz,
    duration_minutes integer NOT NULL,
    note text,
    task_update text NOT NULL DEFAULT 'NONE',
    is_deleted boolean NOT NULL DEFAULT false,
    hlc_map jsonb NOT NULL DEFAULT '{}'::jsonb,
    server_version bigint NOT NULL,
    server_updated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY(task_id) REFERENCES task(id)
);

CREATE INDEX IF NOT EXISTS idx_pomodoro_session_user_server_version
ON pomodoro_session(user_id, server_version);

CREATE INDEX IF NOT EXISTS idx_pomodoro_session_task_id
ON pomodoro_session(task_id);

CREATE TRIGGER trg_pomodoro_session_sync_metadata
BEFORE INSERT OR UPDATE ON pomodoro_session
FOR EACH ROW
EXECUTE FUNCTION assign_sync_metadata();

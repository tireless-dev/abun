CREATE TABLE IF NOT EXISTS sync_server_version (
    id INTEGER PRIMARY KEY,
    next_value BIGINT NOT NULL
);

INSERT INTO sync_server_version (id, next_value)
VALUES (1, 0)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS preference (
    user_id VARCHAR(255) NOT NULL,
    pref_key TEXT NOT NULL,
    pref_value TEXT,
    value_type TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, pref_key)
);

CREATE INDEX IF NOT EXISTS idx_preference_user_server_version ON preference(user_id, server_version);

CREATE TABLE IF NOT EXISTS routine (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    template_title TEXT NOT NULL,
    cron_schedule TEXT NOT NULL,
    timezone TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_routine_user_server_version ON routine(user_id, server_version);

CREATE TABLE IF NOT EXISTS task (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    parent_id VARCHAR(255),
    routine_id VARCHAR(255),
    title TEXT NOT NULL,
    detail TEXT,
    start_not_before VARCHAR(255),
    end_not_after VARCHAR(255),
    estimated_duration VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

ALTER TABLE task ADD COLUMN IF NOT EXISTS detail TEXT;
ALTER TABLE task ADD COLUMN IF NOT EXISTS start_not_before VARCHAR(255);
ALTER TABLE task ADD COLUMN IF NOT EXISTS end_not_after VARCHAR(255);
ALTER TABLE task ADD COLUMN IF NOT EXISTS estimated_duration VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_task_user_server_version ON task(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_task_parent_id ON task(parent_id);

CREATE TABLE IF NOT EXISTS alarm (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    trigger_time VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alarm_user_server_version ON alarm(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_alarm_task_id ON alarm(task_id);

CREATE TABLE IF NOT EXISTS task_event (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    journal_date VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    content TEXT,
    postponed_json TEXT,
    event_time VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

ALTER TABLE task_event ADD COLUMN IF NOT EXISTS postponed_json TEXT;

CREATE INDEX IF NOT EXISTS idx_task_event_user_server_version ON task_event(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_task_event_user_journal_date ON task_event(user_id, journal_date);
CREATE INDEX IF NOT EXISTS idx_task_event_task_id ON task_event(task_id);

CREATE TABLE IF NOT EXISTS pomodoro_session (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255),
    phase VARCHAR(64) NOT NULL,
    state VARCHAR(64) NOT NULL,
    started_at VARCHAR(255) NOT NULL,
    ends_at VARCHAR(255) NOT NULL,
    completed_at VARCHAR(255),
    duration_minutes INTEGER NOT NULL,
    note TEXT,
    task_update VARCHAR(64) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pomodoro_session_user_server_version ON pomodoro_session(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_pomodoro_session_task_id ON pomodoro_session(task_id);

CREATE TABLE IF NOT EXISTS user_account (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(320) UNIQUE NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS otp_code (
    email VARCHAR(320) PRIMARY KEY,
    code VARCHAR(16) NOT NULL,
    expires_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);

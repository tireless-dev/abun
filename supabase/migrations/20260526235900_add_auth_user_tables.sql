CREATE TABLE IF NOT EXISTS user_account (
    id text PRIMARY KEY,
    email text UNIQUE NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS otp_code (
    email text PRIMARY KEY,
    code text NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

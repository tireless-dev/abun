INSERT INTO user_account(id, email, created_at)
VALUES ('abun', 'abun@tireless.dev', '2026-06-03T00:00:00Z')
ON CONFLICT (id) DO UPDATE
SET email = EXCLUDED.email;

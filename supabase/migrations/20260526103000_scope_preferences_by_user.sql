ALTER TABLE preference
ADD COLUMN IF NOT EXISTS user_id text;

UPDATE preference
SET user_id = COALESCE(user_id, 'demo-user')
WHERE user_id IS NULL;

ALTER TABLE preference
ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE preference
DROP CONSTRAINT IF EXISTS preference_pkey;

ALTER TABLE preference
ADD CONSTRAINT preference_pkey PRIMARY KEY (user_id, pref_key);

DROP INDEX IF EXISTS idx_preference_server_version;

CREATE INDEX IF NOT EXISTS idx_preference_user_server_version
ON preference(user_id, server_version);

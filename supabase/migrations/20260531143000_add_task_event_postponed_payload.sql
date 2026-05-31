ALTER TABLE task_event
ADD COLUMN IF NOT EXISTS postponed_json TEXT;

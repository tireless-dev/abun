ALTER TABLE routine ADD COLUMN IF NOT EXISTS template_detail TEXT;
ALTER TABLE routine ADD COLUMN IF NOT EXISTS recurrence_rule TEXT;
ALTER TABLE routine ADD COLUMN IF NOT EXISTS default_start_not_before VARCHAR(255);
ALTER TABLE routine ADD COLUMN IF NOT EXISTS default_estimated_duration VARCHAR(255);

UPDATE routine
SET recurrence_rule = COALESCE(recurrence_rule, cron_schedule)
WHERE recurrence_rule IS NULL;

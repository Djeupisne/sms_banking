-- Rename from_phone -> sender
ALTER TABLE sms_logs RENAME COLUMN from_phone TO sender;

-- Rename to_phone -> "to"
ALTER TABLE sms_logs RENAME COLUMN to_phone TO "to";

-- Rename content -> body
ALTER TABLE sms_logs RENAME COLUMN content TO body;

-- Add missing columns
ALTER TABLE sms_logs ADD COLUMN related_sms_id BIGINT;
ALTER TABLE sms_logs ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
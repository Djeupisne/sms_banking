-- Add OTP key column to clients table for persistent TOTP keys
ALTER TABLE clients ADD COLUMN otp_key VARCHAR(512);

-- Add index for faster lookups by phone number when validating OTP
CREATE INDEX IF NOT EXISTS idx_clients_phone_active ON clients(phone_number, active);

COMMENT ON COLUMN clients.otp_key IS 'Base64-encoded HMAC-SHA256 derived OTP key unique per client';

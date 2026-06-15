-- Create sms_logs table
CREATE TABLE sms_logs (
    id BIGSERIAL PRIMARY KEY,
    from_phone VARCHAR(15) NOT NULL,
    to_phone VARCHAR(15) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    error_message TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_successfully BOOLEAN DEFAULT TRUE
);

-- Create indexes for faster queries
CREATE INDEX idx_sms_logs_from_phone ON sms_logs(from_phone);
CREATE INDEX idx_sms_logs_to_phone ON sms_logs(to_phone);
CREATE INDEX idx_sms_logs_direction ON sms_logs(direction);
CREATE INDEX idx_sms_logs_timestamp ON sms_logs(timestamp);
CREATE INDEX idx_sms_logs_phone_timestamp ON sms_logs(from_phone, timestamp);

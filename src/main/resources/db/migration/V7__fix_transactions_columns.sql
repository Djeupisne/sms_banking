-- Add missing created_at column to transactions table
ALTER TABLE transactions
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
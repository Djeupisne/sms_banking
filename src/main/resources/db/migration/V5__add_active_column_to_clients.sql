-- Add missing active column to clients table
ALTER TABLE clients
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
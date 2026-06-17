-- ============================================================
-- MIGRATION UNIQUE - SCHEMA INITIAL COMPLET
-- Fusion de V1 à V15
-- ============================================================

-- ============================================================
-- 1. TABLE CLIENTS (V1 + V5 + V9 + V13)
-- ============================================================
CREATE TABLE IF NOT EXISTS clients (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    email VARCHAR(255),
    date_of_birth TIMESTAMP,
    address TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    otp_key VARCHAR(512),
    password VARCHAR(255),
    password_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_clients_phone_number ON clients(phone_number);
CREATE INDEX IF NOT EXISTS idx_clients_phone_active ON clients(phone_number, active);

-- ============================================================
-- 2. TABLE ACCOUNTS (V2 + V11)
-- ============================================================
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(50) UNIQUE NOT NULL,
    client_id BIGINT REFERENCES clients(id) ON DELETE CASCADE,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'XOF',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    account_type VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    fee_type VARCHAR(50),
    is_system_account BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_accounts_client_id ON accounts(client_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_accounts_active ON accounts(active);
CREATE INDEX IF NOT EXISTS idx_accounts_is_system_account ON accounts(is_system_account);
CREATE INDEX IF NOT EXISTS idx_accounts_fee_type ON accounts(fee_type);

-- ============================================================
-- 3. TABLE USERS (V14)
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_login TIMESTAMP
);

-- ============================================================
-- 4. TABLE TRANSACTIONS (V4 + V7)
-- ============================================================
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    related_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    reference VARCHAR(100),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_transaction_id ON transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_timestamp ON transactions(account_id, timestamp);

-- ============================================================
-- 5. TABLE SMS_LOGS (V3 + V6)
-- ============================================================
CREATE TABLE IF NOT EXISTS sms_logs (
    id BIGSERIAL PRIMARY KEY,
    sender VARCHAR(15) NOT NULL,
    "to" VARCHAR(15) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    error_message TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_successfully BOOLEAN DEFAULT TRUE,
    related_sms_id BIGINT,
    reference VARCHAR(50) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sms_logs_sender ON sms_logs(sender);
CREATE INDEX IF NOT EXISTS idx_sms_logs_timestamp ON sms_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_sms_logs_reference ON sms_logs(reference);

-- ============================================================
-- 6. TABLE TRANSACTION_LOGS (V15)
-- ============================================================
CREATE TABLE IF NOT EXISTS transaction_logs (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    user_role VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    source_account VARCHAR(100),
    target_account VARCHAR(100),
    source_phone VARCHAR(50),
    target_phone VARCHAR(50),
    description VARCHAR(500),
    transaction_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    ip_address VARCHAR(50) NOT NULL,
    user_agent VARCHAR(1000) NOT NULL,
    fees_amount DECIMAL(15,2),
    total_amount DECIMAL(15,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transaction_logs_username ON transaction_logs(username);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_created_at ON transaction_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_status ON transaction_logs(status);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_transaction_type ON transaction_logs(transaction_type);

-- ============================================================
-- 7. TABLE PASSWORD_RESET_TOKENS (V13)
-- ============================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_email ON password_reset_tokens(email);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expiry ON password_reset_tokens(expiry_date);

-- ============================================================
-- DONNÉES DE TEST
-- ============================================================

INSERT INTO clients (first_name, last_name, phone_number, email, date_of_birth, address, active, created_at, updated_at)
VALUES
('Hermann', 'AKUE', '+22890000001', 'hermann.akue@email.com', '1985-03-15', 'Lome, Togo', TRUE, NOW(), NOW()),
('Elom', 'AFEZOUNON', '+22890000002', 'elom.afezounon@email.com', '1990-07-22', 'Lome, Togo', TRUE, NOW(), NOW()),
('Valion', 'KPIZIA', '+22890000003', 'valion.kpizia@email.com', '1988-11-08', 'Kara, Togo', TRUE, NOW(), NOW()),
('Essomina', 'ROYODE', '+22890000004', 'essomina.royode@email.com', '1992-05-20', 'Sokode, Togo', TRUE, NOW(), NOW())
ON CONFLICT (phone_number) DO NOTHING;

INSERT INTO accounts (account_number, client_id, balance, currency, active, account_type, status, created_at, updated_at)
SELECT 'COMPTE001', id, 500000.00, 'XOF', TRUE, 'CURRENT', 'ACTIVE', NOW(), NOW()
FROM clients WHERE phone_number = '+22890000001'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (account_number, client_id, balance, currency, active, account_type, status, created_at, updated_at)
SELECT 'COMPTE002', id, 750000.00, 'XOF', TRUE, 'SAVINGS', 'ACTIVE', NOW(), NOW()
FROM clients WHERE phone_number = '+22890000002'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (account_number, client_id, balance, currency, active, account_type, status, created_at, updated_at)
SELECT 'COMPTE003', id, 250000.00, 'XOF', TRUE, 'CURRENT', 'ACTIVE', NOW(), NOW()
FROM clients WHERE phone_number = '+22890000003'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (account_number, client_id, balance, currency, active, account_type, status, created_at, updated_at)
SELECT 'COMPTE004', id, 100000.00, 'XOF', TRUE, 'CURRENT', 'ACTIVE', NOW(), NOW()
FROM clients WHERE phone_number = '+22890000004'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (account_number, client_id, balance, currency, active, account_type, status, created_at, updated_at)
SELECT 'COMPTE005', id, 250000.00, 'XOF', TRUE, 'SAVINGS', 'ACTIVE', NOW(), NOW()
FROM clients WHERE phone_number = '+22890000001'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (
    account_number,
    client_id,
    balance,
    currency,
    active,
    account_type,
    status,
    is_system_account,
    description,
    fee_type,
    created_at,
    updated_at
) VALUES (
    'FEE_MOBILE_MONEY_001',
    NULL,
    0.00,
    'XOF',
    TRUE,
    'CURRENT',
    'ACTIVE',
    TRUE,
    'Compte collecteur des frais Mobile Money',
    'MOBILE_MONEY_TRANSFER',
    NOW(),
    NOW()
) ON CONFLICT (account_number) DO NOTHING;

INSERT INTO users (username, password, full_name, email, role, active, created_at)
VALUES (
    'admin',
    '$2a$12$0iA4OLd/sKwkaerupX90jOxbfOqJZiunFicUzFRS2//sLnNU4/RHO',
    'Administrateur',
    'admin@orabank.tg',
    'ADMIN',
    TRUE,
    NOW()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password, full_name, email, role, active, created_at)
VALUES (
    'viewer',
    '$2a$12$ZySs/33V.lusmnDiQELCnOZioSBSCwg4K56pr23Ziux3eGIhusqbK',
    'Utilisateur Viewer',
    'viewer@orabank.tg',
    'USER',
    TRUE,
    NOW()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, type, amount, status, description, created_at)
SELECT 'TXN001', id, 'CREDIT', 100000.00, 'COMPLETED', 'Depot initial', NOW()
FROM accounts WHERE account_number = 'COMPTE001'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, type, amount, status, description, created_at)
SELECT 'TXN002', id, 'DEBIT', 5000.00, 'COMPLETED', 'Achat Mobile Money', NOW()
FROM accounts WHERE account_number = 'COMPTE001'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, type, amount, status, description, created_at)
SELECT 'TXN003', id, 'CREDIT', 150000.00, 'COMPLETED', 'Depot salaire', NOW()
FROM accounts WHERE account_number = 'COMPTE002'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, type, amount, status, description, created_at)
SELECT 'TXN004', id, 'CREDIT', 50000.00, 'COMPLETED', 'Cadeau', NOW()
FROM accounts WHERE account_number = 'COMPTE003'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, type, amount, status, description, created_at)
SELECT 'TXN005', id, 'CREDIT', 100000.00, 'COMPLETED', 'Depot initial', NOW()
FROM accounts WHERE account_number = 'COMPTE004'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO sms_logs (sender, "to", direction, body, timestamp, processed_successfully, reference, created_at)
SELECT '+22890000001', 'ORABANK', 'INCOMING', 'SOLDE?', NOW(), TRUE, 'SMS_' || to_char(NOW(), 'YYYYMMDDHH24MISS') || '_0001', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sms_logs LIMIT 1);

INSERT INTO sms_logs (sender, "to", direction, body, timestamp, processed_successfully, reference, created_at)
SELECT 'ORABANK', '+22890000001', 'OUTGOING', 'ORABANK - Votre solde est de: 500000 FCFA', NOW(), TRUE, 'SMS_' || to_char(NOW(), 'YYYYMMDDHH24MISS') || '_0002', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sms_logs WHERE direction = 'OUTGOING');

-- ============================================================
-- FONCTIONS ET TRIGGERS
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_clients_updated_at ON clients;
CREATE TRIGGER update_clients_updated_at BEFORE UPDATE ON clients
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_accounts_updated_at ON accounts;
CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
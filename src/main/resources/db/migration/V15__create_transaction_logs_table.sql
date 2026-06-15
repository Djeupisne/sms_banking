-- Création de la table transaction_logs
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

-- Création des index
CREATE INDEX IF NOT EXISTS idx_transaction_logs_username ON transaction_logs(username);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_created_at ON transaction_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_status ON transaction_logs(status);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_transaction_type ON transaction_logs(transaction_type);
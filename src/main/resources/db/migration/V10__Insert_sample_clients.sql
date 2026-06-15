-- =====================================================
-- Insertion des clients de test
-- =====================================================

-- Insertion des clients togolais
INSERT INTO clients (first_name, last_name, phone_number, email, date_of_birth, address, created_at, updated_at, active)
VALUES
('Hermann', 'AKUE', '+22890000001', 'hermann.akue@email.com', '1985-03-15', 'Lomé, Togo', NOW(), NOW(), TRUE),
('Elom', 'AFEZOUNON', '+22890000002', 'elom.afezounon@email.com', '1990-07-22', 'Lomé, Togo', NOW(), NOW(), TRUE),
('Valion', 'KPIZIA', '+22890000003', 'valion.kpizia@email.com', '1988-11-08', 'Kara, Togo', NOW(), NOW(), TRUE),
('Essomina', 'ROYODE', '+22890000004', 'essomina.royode@email.com', '1992-05-20', 'Sokodé, Togo', NOW(), NOW(), TRUE)
ON CONFLICT (phone_number) DO NOTHING;

-- Insertion des comptes
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

-- Insertion des transactions
INSERT INTO transactions (transaction_id, account_id, related_account_id, type, amount, currency, status, description, reference, created_at)
SELECT 'TXN001', a.id, NULL, 'CREDIT', 100000.00, 'XOF', 'COMPLETED', 'Dépôt initial', 'INIT001', NOW()
FROM accounts a WHERE a.account_number = 'COMPTE001'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, related_account_id, type, amount, currency, status, description, reference, created_at)
SELECT 'TXN002', a.id, NULL, 'DEBIT', 5000.00, 'XOF', 'COMPLETED', 'Achat Mobile Money', 'MM001', NOW()
FROM accounts a WHERE a.account_number = 'COMPTE001'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, related_account_id, type, amount, currency, status, description, reference, created_at)
SELECT 'TXN003', a.id, NULL, 'CREDIT', 150000.00, 'XOF', 'COMPLETED', 'Dépôt salaire', 'SAL001', NOW()
FROM accounts a WHERE a.account_number = 'COMPTE002'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, related_account_id, type, amount, currency, status, description, reference, created_at)
SELECT 'TXN004', a.id, NULL, 'CREDIT', 50000.00, 'XOF', 'COMPLETED', 'Cadeau', 'GIFT001', NOW()
FROM accounts a WHERE a.account_number = 'COMPTE003'
ON CONFLICT (transaction_id) DO NOTHING;

INSERT INTO transactions (transaction_id, account_id, related_account_id, type, amount, currency, status, description, reference, created_at)
SELECT 'TXN005', a.id, NULL, 'CREDIT', 100000.00, 'XOF', 'COMPLETED', 'Dépôt initial', 'INIT004', NOW()
FROM accounts a WHERE a.account_number = 'COMPTE004'
ON CONFLICT (transaction_id) DO NOTHING;

-- Insertion des logs SMS
INSERT INTO sms_logs (sender, "to", direction, body, error_message, timestamp, processed_successfully, related_sms_id, created_at)
SELECT '+22890000001', '+22801010101', 'OUTBOUND', 'ORABANK - Votre solde est de: 500000 FCFA', NULL, NOW(), TRUE, NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sms_logs WHERE sender = '+22890000001');

INSERT INTO sms_logs (sender, "to", direction, body, error_message, timestamp, processed_successfully, related_sms_id, created_at)
SELECT '+22890000002', '+22801010101', 'OUTBOUND', 'ORABANK - Votre solde est de: 750000 FCFA', NULL, NOW(), TRUE, NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sms_logs WHERE sender = '+22890000002');
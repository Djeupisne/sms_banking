-- ============================================================
-- MIGRATION V11: Ajout des colonnes pour la gestion des frais
-- ============================================================

-- 1. Modifier d'abord la colonne currency pour accepter plus de caractères
ALTER TABLE accounts
ALTER COLUMN currency TYPE VARCHAR(10);

-- 2. Ajouter les nouvelles colonnes
ALTER TABLE accounts
ADD COLUMN IF NOT EXISTS description TEXT,
ADD COLUMN IF NOT EXISTS fee_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS is_system_account BOOLEAN DEFAULT FALSE;

-- 3. Modifier la colonne client_id pour permettre NULL (comptes système)
ALTER TABLE accounts
ALTER COLUMN client_id DROP NOT NULL;

-- 4. Créer des index pour les nouvelles colonnes
CREATE INDEX IF NOT EXISTS idx_accounts_is_system_account ON accounts(is_system_account);
CREATE INDEX IF NOT EXISTS idx_accounts_fee_type ON accounts(fee_type);

-- 5. Créer le compte de frais Mobile Money (compte système)
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
    'FCFA',  -- Maintenant 4 caractères est accepté
    true,
    'CURRENT',
    'ACTIVE',
    true,
    'Compte collecteur des frais pour les transferts Mobile Money',
    'MOBILE_MONEY_TRANSFER',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (account_number) DO NOTHING;

-- 6. Mettre à jour tous les comptes existants pour utiliser 'XOF' au lieu de 'FCFA' (optionnel)
UPDATE accounts
SET currency = 'XOF'
WHERE currency = 'FCFA' AND account_number != 'FEE_MOBILE_MONEY_001';

-- 7. Ajouter des commentaires sur les colonnes
COMMENT ON COLUMN accounts.is_system_account IS 'Indique si le compte est un compte système (frais, commissions, etc.)';
COMMENT ON COLUMN accounts.fee_type IS 'Type de frais pour les comptes système (MOBILE_MONEY_TRANSFER, ATM_WITHDRAWAL, etc.)';
COMMENT ON COLUMN accounts.description IS 'Description du compte (spécialement pour les comptes système)';
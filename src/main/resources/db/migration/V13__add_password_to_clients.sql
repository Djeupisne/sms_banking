-- ============================================================
-- MIGRATION V13: Ajout du mot de passe et table de tokens
-- ============================================================

-- Ajouter la colonne password aux clients
ALTER TABLE clients
ADD COLUMN IF NOT EXISTS password VARCHAR(255),
ADD COLUMN IF NOT EXISTS password_updated_at TIMESTAMP;

-- Créer la table des tokens de réinitialisation
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Créer les index
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_email ON password_reset_tokens(email);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expiry ON password_reset_tokens(expiry_date);

-- Créer un mot de passe par défaut pour les clients existants (à modifier à la première connexion)
-- Le mot de passe par défaut est "default123" encodé en BCrypt
-- UPDATE clients SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi' WHERE password IS NULL;
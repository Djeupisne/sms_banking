-- Supprime la contrainte d'unicite sur sms_logs.reference.
--
-- Design voulu : une meme reference (conversationReference) est partagee
-- par le SMS entrant (INCOMING) et le SMS sortant (OUTGOING) d'un meme
-- echange, afin de pouvoir les identifier/regrouper facilement.
-- La liaison stricte entre les deux lignes reste assuree par
-- relatedSmsId (id du SMS entrant reference depuis le SMS sortant).
--
-- Sans cette migration, deux SmsLog partageant la meme reference
-- provoquent une violation de contrainte unique
-- (org.postgresql.util.PSQLException: duplicate key value violates
-- unique constraint "sms_logs_reference_key").

ALTER TABLE sms_logs DROP CONSTRAINT IF EXISTS sms_logs_reference_key;

-- Un index (non unique) est conserve sur reference pour les performances
-- de recherche/regroupement par conversation.
CREATE INDEX IF NOT EXISTS idx_sms_logs_reference ON sms_logs (reference);

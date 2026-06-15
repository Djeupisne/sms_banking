-- Migration pour normaliser les numéros de téléphone au format E.164 (+225XXXXXXXXX)
-- Cette migration s'assure que tous les numéros de téléphone dans la table clients
-- ont le préfixe '+' pour correspondre au format utilisé par les webhooks SMS

-- Ajouter le '+' aux numéros qui n'en ont pas déjà un
UPDATE clients 
SET phone_number = '+' || phone_number 
WHERE phone_number NOT LIKE '+%' AND phone_number IS NOT NULL;

-- Vérification : afficher les numéros normalisés (pour débogage)
-- SELECT phone_number FROM clients ORDER BY phone_number;

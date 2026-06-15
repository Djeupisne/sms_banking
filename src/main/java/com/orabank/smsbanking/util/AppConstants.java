package com.orabank.smsbanking.util;

/**
 * Constantes de l'application pour éviter les magic numbers.
 * Centralise toutes les valeurs numériques et configurations statiques.
 */
public final class AppConstants {

    /**
     * Durée de validité maximale d'un webhook en millisecondes (5 minutes).
     * Utilisé pour la prévention des attaques replay.
     */
    public static final long MAX_WEBHOOK_AGE_MS = 300_000L;

    /**
     * Longueur minimale d'un numéro de téléphone pour le masquage.
     */
    public static final int MIN_PHONE_NUMBER_LENGTH = 4;

    /**
     * Longueur minimale d'un numéro de compte pour le masquage.
     */
    public static final int MIN_ACCOUNT_NUMBER_LENGTH = 4;

    /**
     * Montant maximum de transfert autorisé (en FCFA).
     */
    public static final long MAX_TRANSFER_AMOUNT = 1_000_000L;

    /**
     * Timeout de lecture pour les appels API externes (en millisecondes).
     */
    public static final int API_READ_TIMEOUT_MS = 10_000;

    /**
     * Timeout de connexion pour les appels API externes (en millisecondes).
     */
    public static final int API_CONNECT_TIMEOUT_MS = 5_000;

    /**
     * Durée de validité par défaut d'un OTP (en minutes).
     */
    public static final int DEFAULT_OTP_VALIDITY_MINUTES = 5;

    /**
     * Nombre de chiffres pour le code TOTP.
     */
    public static final int TOTP_DIGITS = 6;

    /**
     * Fenêtre de validation TOTP (nombre de périodes autorisées).
     */
    public static final int TOTP_WINDOW_SIZE = 1;

    /**
     * Délai entre les tentatives de réessai (en millisecondes).
     */
    public static final int RETRY_DELAY_MS = 1_000;

    /**
     * Nombre maximum de tentatives de réessai.
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Seuil d'ouverture du circuit breaker (pourcentage d'échecs).
     */
    public static final float CIRCUIT_BREAKER_FAILURE_THRESHOLD = 50.0f;

    /**
     * Durée de la fenêtre glissante du circuit breaker (en secondes).
     */
    public static final int CIRCUIT_BREAKER_SLIDING_WINDOW_SIZE = 10;

    /**
     * Durée d'attente avant réouverture du circuit breaker (en secondes).
     */
    public static final int CIRCUIT_BREAKER_WAIT_DURATION_SECONDS = 60;

    /**
     * Limite de requêtes par seconde pour le rate limiting.
     */
    public static final int RATE_LIMIT_REQUESTS_PER_SECOND = 10;

    /**
     * Durée d'expiration des clés Redis (en secondes).
     */
    public static final int REDIS_KEY_EXPIRY_SECONDS = 3600;

    /**
     * Préfixe pour les clés de rate limiting dans Redis.
     */
    public static final String RATE_LIMIT_REDIS_PREFIX = "ratelimit:";

    /**
     * Préfixe pour les clés de session OTP dans Redis.
     */
    public static final String OTP_REDIS_PREFIX = "otp:";

    /**
     * Préfixe pour les clés de saga dans Redis.
     */
    public static final String SAGA_REDIS_PREFIX = "saga:";

    // Empêcher l'instanciation
    private AppConstants() {
        throw new IllegalStateException("Utility class cannot be instantiated");
    }
}

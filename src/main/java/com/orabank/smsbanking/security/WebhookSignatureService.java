package com.orabank.smsbanking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

/**
 * Service pour la génération et la validation de signatures HMAC pour les webhooks.
 * Utilise HMAC-SHA256 pour assurer l'intégrité et l'authenticité des requêtes webhook.
 *
 * NOTE IMPORTANT : La clé secrète (webhook.secret.key) est attendue comme une chaîne de caractères BRUTE (UTF-8).
 * Elle ne doit PAS être encodée en hexadécimal dans le fichier de configuration.
 * Cela correspond au comportement par défaut de CryptoJS.HmacSHA256(payload, secretString).
 */
@Slf4j
@Service
public class WebhookSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] secretKeyBytes;

    public WebhookSignatureService(@Value("${webhook.secret.key}") String secretKey) {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalArgumentException("Webhook secret key cannot be null or empty");
        }

        // CORRECTION : On utilise directement les bytes UTF-8 de la chaîne.
        // C'est ainsi que CryptoJS traite la clé quand on passe une string.
        this.secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);

        log.info("Webhook signature service initialized. Clé chargée ({} octets).", secretKeyBytes.length);
    }

    /**
     * Génère une signature HMAC-SHA256 encodée en hexadécimal.
     * Format du payload signé : "{timestamp}:{payload}"
     *
     * @param payload   le corps formaté (from|to|body)
     * @param timestamp le timestamp de la requête (millisecondes depuis epoch)
     * @return la signature en hexadécimal minuscule
     */
    public String generateSignature(String payload, long timestamp) {
        try {
            String dataToSign = timestamp + ":" + payload;

            Mac sha256HMAC = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKeyBytes, HMAC_SHA256);
            sha256HMAC.init(secretKeySpec);

            byte[] hash = sha256HMAC.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));

            // Encodage hex (compatible avec CryptoJS.HmacSHA256().toString())
            return toHexString(hash);

        } catch (NoSuchAlgorithmException e) {
            log.error("Algorithm not found", e);
            throw new RuntimeException("HMAC-SHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            log.error("Invalid key for HMAC", e);
            throw new RuntimeException("Invalid webhook secret key", e);
        } catch (Exception e) {
            log.error("Error generating signature", e);
            throw new RuntimeException("Error generating webhook signature", e);
        }
    }

    /**
     * Valide la signature d'une requête webhook.
     *
     * @param payload           le payload formaté (from|to|body)
     * @param timestamp         le timestamp de la requête
     * @param receivedSignature la signature reçue dans le header X-Webhook-Signature
     * @return true si la signature est valide, false sinon
     */
    public boolean validateSignature(String payload, long timestamp, String receivedSignature) {
        try {
            String expectedSignature = generateSignature(payload, timestamp);

            log.debug("Payload pour signature: {}", timestamp + ":" + payload);
            log.debug("Signature attendue  : {}", expectedSignature);
            log.debug("Signature reçue     : {}", receivedSignature);

            // Comparaison en temps constant pour éviter les attaques par timing
            return constantTimeEquals(expectedSignature, receivedSignature);
        } catch (Exception e) {
            log.error("Error validating signature", e);
            return false;
        }
    }

    /**
     * Compare deux chaînes en temps constant pour éviter les attaques par timing.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Convertit un tableau d'octets en chaîne hexadécimale minuscule.
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        try (Formatter formatter = new Formatter(sb)) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
        }
        return sb.toString();
    }

    /**
     * Vérifie si le timestamp est dans une fenêtre de temps acceptable.
     */
    public boolean isTimestampValid(long timestamp, long maxAgeMs) {
        long currentTime = System.currentTimeMillis();
        long age = currentTime - timestamp;
        return age >= 0 && age <= maxAgeMs;
    }
}
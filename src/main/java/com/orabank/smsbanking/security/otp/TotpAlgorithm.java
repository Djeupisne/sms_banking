package com.orabank.smsbanking.security.otp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Implémentation de la génération TOTP conforme RFC 6238.
 * Cette classe est responsable uniquement de l'algorithme HOTP/TOTP.
 */
@Slf4j
@Component
public class TotpAlgorithm {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int[] DIGITS_POWER = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    /**
     * Génère un code TOTP basé sur une clé secrète et un timestamp.
     *
     * @param secretKey La clé secrète du client
     * @param timeStep  Le pas de temps (généralement 30 secondes)
     * @param otpLength La longueur du code OTP (6-8 chiffres)
     * @return Le code OTP généré
     */
    public String generateTotp(String secretKey, long timeStep, int otpLength) {
        try {
            // Étape 1: Encoder la clé en Base64 et préparer HMAC
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            
            // Étape 2: Calculer le counter basé sur le temps
            long counter = timeStep / 30; // 30 secondes par défaut
            
            // Étape 3: Créer le message (counter big-endian 8 bytes)
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }
            
            // Étape 4: Calculer HMAC-SHA256
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data);
            
            // Étape 5: Dynamic Truncation (RFC 4226)
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) |
                        ((hash[offset + 1] & 0xFF) << 16) |
                        ((hash[offset + 2] & 0xFF) << 8) |
                        (hash[offset + 3] & 0xFF);
            
            // Étape 6: Générer le code OTP
            int otp = binary % DIGITS_POWER[otpLength];
            
            // Formater avec des zéros non-significatifs si nécessaire
            return String.format("%0" + otpLength + "d", otp);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Erreur lors de la génération TOTP", e);
            throw new IllegalStateException("Impossible de générer le code OTP", e);
        }
    }

    /**
     * Valide un code OTP fourni par l'utilisateur.
     *
     * @param secretKey La clé secrète du client
     * @param userCode  Le code fourni par l'utilisateur
     * @param timeStep  Le pas de temps actuel
     * @param otpLength La longueur attendue du code
     * @param window    La fenêtre de validation (nombre de pas de temps acceptés)
     * @return true si le code est valide
     */
    public boolean validateTotp(String secretKey, String userCode, long timeStep, int otpLength, int window) {
        // Vérifier le code actuel et les codes précédents/suivants dans la fenêtre
        for (int i = -window; i <= window; i++) {
            long adjustedTimeStep = timeStep + (i * 30);
            String expectedCode = generateTotp(secretKey, adjustedTimeStep, otpLength);
            
            if (constantTimeEquals(userCode, expectedCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comparaison en temps constant pour éviter les attaques par timing.
     */
    public boolean constantTimeEquals(String a, String b) {
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
     * Calcule le temps restant avant expiration du code OTP actuel.
     *
     * @return Temps restant en secondes
     */
    public int getRemainingSeconds() {
        long currentTime = Instant.now().getEpochSecond();
        return 30 - (int) (currentTime % 30);
    }
}

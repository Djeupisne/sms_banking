package com.orabank.smsbanking.security.otp;

import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.repository.ClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Service de gestion des clés OTP et coordination de la génération.
 * Cette classe gère la persistance des clés clients et délègue l'algorithme à TotpAlgorithm.
 */
@Slf4j
@Component
public class OtpKeyManager {

    private final ClientRepository clientRepository;
    private final TotpAlgorithm totpAlgorithm;
    
    @Value("${otp.length:6}")
    private int otpLength;
    
    @Value("${otp.validity.minutes:5}")
    private int otpValidityMinutes;
    
    @Value("${otp.master.secret:ORABANK_MASTER_OTP_SECRET_2024_SECURE_KEY_MIN_32_CHARS}")
    private String otpMasterSecret;
    
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpKeyManager(ClientRepository clientRepository, TotpAlgorithm totpAlgorithm) {
        this.clientRepository = clientRepository;
        this.totpAlgorithm = totpAlgorithm;
    }

    /**
     * Initialise ou récupère la clé OTP pour un client.
     */
    @Transactional
    public String getOrCreateClientOtpKey(String phoneNumber) {
        // Normaliser le numéro de téléphone
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        
        return clientRepository.findByPhoneNumber(normalizedPhone)
            .map(client -> {
                if (client.getOtpKey() == null || client.getOtpKey().isEmpty()) {
                    log.info("Génération d'une nouvelle clé OTP pour le client {}", 
                            LoggingUtil.maskPhoneNumber(normalizedPhone));
                    String newKey = generateClientSpecificKey(normalizedPhone);
                    client.setOtpKey(newKey);
                    clientRepository.save(client);
                    return newKey;
                }
                return client.getOtpKey();
            })
            .orElseThrow(() -> {
                log.warn("Client non trouvé pour le numéro: {}", 
                        LoggingUtil.maskPhoneNumber(normalizedPhone));
                return new IllegalArgumentException("Client non trouvé");
            });
    }

    /**
     * Génère une clé spécifique au client basée sur la clé maîtresse.
     */
    public String generateClientSpecificKey(String phoneNumber) {
        try {
            // Combiner la clé maîtresse avec le numéro de téléphone et un salt aléatoire
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            
            String saltedInput = otpMasterSecret + ":" + phoneNumber + ":" + bytesToHex(salt);
            
            // Encoder en Base64
            return Base64.getEncoder().encodeToString(saltedInput.getBytes());
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération de la clé client", e);
            throw new IllegalStateException("Impossible de générer la clé OTP", e);
        }
    }

    /**
     * Génère un code OTP pour un client.
     */
    public String generateOtpForClient(String phoneNumber) {
        String clientKey = getOrCreateClientOtpKey(phoneNumber);
        long currentTimeStep = Instant.now().getEpochSecond();
        
        String otp = totpAlgorithm.generateTotp(clientKey, currentTimeStep, otpLength);
        
        log.info("OTP généré pour {}", LoggingUtil.maskPhoneNumber(phoneNumber));
        
        return otp;
    }

    /**
     * Valide un code OTP fourni par un client.
     */
    public boolean validateOtpForClient(String phoneNumber, String userCode) {
        try {
            String clientKey = getOrCreateClientOtpKey(phoneNumber);
            long currentTimeStep = Instant.now().getEpochSecond();
            
            // Fenêtre de validation de 2 pas (±1 minute)
            boolean isValid = totpAlgorithm.validateTotp(
                clientKey, 
                userCode, 
                currentTimeStep, 
                otpLength, 
                2
            );
            
            if (isValid) {
                log.info("OTP valide pour {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            } else {
                log.warn("Échec validation OTP pour {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            }
            
            return isValid;
            
        } catch (IllegalArgumentException e) {
            log.warn("Validation OTP échouée - client non trouvé", e);
            return false;
        }
    }

    /**
     * Normalise un numéro de téléphone.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("Numéro de téléphone requis");
        }
        
        // Supprimer tous les caractères non numériques sauf le +
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");
        
        // Si commence par 0, remplacer par +228 (Togo)
        if (cleaned.startsWith("0")) {
            cleaned = "+228" + cleaned.substring(1);
        }
        
        // S'assurer qu'il commence par +
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }
        
        // Validation basique
        if (!cleaned.matches("^\\+[0-9]{8,15}$")) {
            throw new IllegalArgumentException("Format de numéro de téléphone invalide: " + 
                    LoggingUtil.maskPhoneNumber(phoneNumber));
        }
        
        return cleaned;
    }

    /**
     * Convertit un tableau de bytes en hexadécimal.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Retourne le temps restant avant expiration du code OTP actuel.
     */
    public int getRemainingSeconds() {
        return totpAlgorithm.getRemainingSeconds();
    }

    /**
     * Retourne la durée de validité des OTP en minutes.
     */
    public int getOtpValidityMinutes() {
        return otpValidityMinutes;
    }
}

// Classe utilitaire pour le masquage des numéros (à déplacer dans util package si existe)
class LoggingUtil {
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return phoneNumber.substring(0, 3) + "****" + 
               phoneNumber.substring(phoneNumber.length() - 2);
    }
}

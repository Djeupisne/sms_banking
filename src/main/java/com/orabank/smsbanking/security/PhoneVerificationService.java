package com.orabank.smsbanking.security;

import com.orabank.smsbanking.gateway.SmsGateway;
import com.orabank.smsbanking.util.LoggingUtil;
import com.orabank.smsbanking.util.SmsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Service for handling phone verification operations.
 * Manages OTP generation, sending, and validation for phone numbers.
 * Utilise Redis pour le stockage des OTP avec TTL automatique.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {
    
    private final OtpGenerator otpGenerator;
    private final SmsGateway smsGateway;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${otp.length:6}")
    private int otpLength;
    
    @Value("${otp.validity.minutes:5}")
    private int otpValidityMinutes;
    
    // Préfixe pour les clés Redis
    private static final String OTP_KEY_PREFIX = "otp:";
    
    /**
     * Generates and sends an OTP to the specified phone number.
     * Stocke l'OTP dans Redis avec TTL automatique.
     * La clé OTP du client est persistée en base de données par OtpGenerator.
     *
     * @param phoneNumber the phone number to send OTP to
     * @return true if OTP was sent successfully, false otherwise
     */
    public boolean generateAndSendOtp(String phoneNumber) {
        try {
            // Nettoyer et normaliser le numéro de téléphone avec l'utilitaire partagé
            String normalizedPhone = SmsUtils.normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                log.error("Numéro de téléphone invalide: {}", phoneNumber);
                return false;
            }
            
            // Générer l'OTP (la clé OTP est gérée de manière persistante par OtpGenerator)
            String otp = otpGenerator.generateOtp(normalizedPhone);
            
            // Stocker l'OTP dans Redis avec TTL
            long validityMs = otpValidityMinutes * 60 * 1000L;
            redisTemplate.opsForValue().set(OTP_KEY_PREFIX + normalizedPhone, otp, validityMs, TimeUnit.MILLISECONDS);
            
            // Envoyer l'OTP par SMS
            String message = String.format("Votre code OTP est: %s (valable %d min)", otp, otpValidityMinutes);
            boolean sent = smsGateway.sendSms(normalizedPhone, message);
            
            if (sent) {
                log.info("OTP sent successfully to masked-phone: {}", LoggingUtil.maskPhoneNumber(normalizedPhone));
            } else {
                log.error("Failed to send OTP to masked-phone: {}", LoggingUtil.maskPhoneNumber(normalizedPhone));
                // Nettoyer les données Redis en cas d'échec d'envoi
                redisTemplate.delete(OTP_KEY_PREFIX + normalizedPhone);
            }
            
            return sent;
        } catch (Exception e) {
            log.error("Error generating and sending OTP", e);
            return false;
        }
    }
    
    /**
     * Verifies an OTP for the given phone number.
     * Récupère l'OTP depuis Redis et le valide avec OtpGenerator.
     *
     * @param phoneNumber the phone number to verify
     * @param otp the OTP to verify
     * @return true if the OTP is valid, false otherwise
     */
    public boolean verifyOtp(String phoneNumber, String otp) {
        try {
            String normalizedPhone = SmsUtils.normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                log.warn("Numéro de téléphone invalide: {}", phoneNumber);
                return false;
            }
            
            // Récupérer l'OTP stocké dans Redis
            String storedOtp = redisTemplate.opsForValue().get(OTP_KEY_PREFIX + normalizedPhone);
            
            if (storedOtp == null) {
                log.warn("Aucun OTP trouvé ou expiré pour masked-phone: {}", LoggingUtil.maskPhoneNumber(normalizedPhone));
                return false;
            }
            
            // Comparaison en temps constant pour éviter les attaques par timing
            boolean isValid = constantTimeEquals(storedOtp, otp);
            
            if (isValid) {
                log.info("OTP verified successfully for masked-phone: {}", LoggingUtil.maskPhoneNumber(normalizedPhone));
                // Supprimer l'OTP après utilisation (one-time use)
                redisTemplate.delete(OTP_KEY_PREFIX + normalizedPhone);
            } else {
                log.warn("Invalid OTP for masked-phone: {}", LoggingUtil.maskPhoneNumber(normalizedPhone));
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying OTP", e);
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
}

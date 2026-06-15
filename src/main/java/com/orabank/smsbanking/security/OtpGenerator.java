package com.orabank.smsbanking.security;

import com.orabank.smsbanking.security.otp.OtpKeyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Facade pour la génération et validation de codes OTP.
 * Délègue la logique métier à OtpKeyManager et TotpAlgorithm.
 * 
 * @see com.orabank.smsbanking.security.otp.OtpKeyManager
 * @see com.orabank.smsbanking.security.otp.TotpAlgorithm
 */
@Slf4j
@Component
public class OtpGenerator {

    private final OtpKeyManager otpKeyManager;

    public OtpGenerator(OtpKeyManager otpKeyManager) {
        this.otpKeyManager = otpKeyManager;
    }

    /**
     * Génère un code OTP pour un client.
     *
     * @param phoneNumber Numéro de téléphone du client
     * @return Le code OTP généré
     */
    public String generateOtp(String phoneNumber) {
        log.debug("Génération OTP pour: {}", maskPhoneNumber(phoneNumber));
        return otpKeyManager.generateOtpForClient(phoneNumber);
    }

    /**
     * Valide un code OTP fourni par un client.
     *
     * @param phoneNumber Numéro de téléphone du client
     * @param code Code OTP à valider
     * @return true si le code est valide
     */
    public boolean validateOtp(String phoneNumber, String code) {
        log.debug("Validation OTP pour: {}", maskPhoneNumber(phoneNumber));
        return otpKeyManager.validateOtpForClient(phoneNumber, code);
    }

    /**
     * Retourne le temps restant avant expiration du code OTP actuel.
     *
     * @return Temps restant en secondes
     */
    public int getRemainingSeconds() {
        return otpKeyManager.getRemainingSeconds();
    }

    /**
     * Retourne la durée de validité des OTP en minutes.
     *
     * @return Durée en minutes
     */
    public int getOtpValidityMinutes() {
        return otpKeyManager.getOtpValidityMinutes();
    }

    /**
     * Utilitaire de masquage des numéros de téléphone pour les logs.
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return phoneNumber.substring(0, 3) + "****" + 
               phoneNumber.substring(phoneNumber.length() - 2);
    }
}

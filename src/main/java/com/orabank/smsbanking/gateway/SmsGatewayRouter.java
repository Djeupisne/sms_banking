package com.orabank.smsbanking.gateway;

import com.orabank.smsbanking.util.LoggingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Primary SMS gateway router for Togo.
 * Routes SMS through Moov Africa Togo (primary) and Togocel (fallback).
 * This replaces Twilio and Orange gateways for Togo operations.
 */
@Slf4j
@Primary
@Service
public class SmsGatewayRouter implements SmsGateway {
    
    private final SmsGateway moovGateway;
    private final SmsGateway togocelGateway;
    private final boolean preferMoov;
    
    public SmsGatewayRouter(
            @Qualifier("moovSmsGateway") SmsGateway moovGateway,
            @Qualifier("togocelSmsGateway") SmsGateway togocelGateway,
            @Value("${sms.gateway.prefer.moov:true}") boolean preferMoov) {
        
        this.moovGateway = moovGateway;
        this.togocelGateway = togocelGateway;
        this.preferMoov = preferMoov;
        
        log.info("SMS Gateway Router initialized - Primary: {}, Fallback: {}", 
                preferMoov ? "Moov" : "Togocel", 
                preferMoov ? "Togocel" : "Moov");
    }
    
    @Override
    public boolean sendSms(String to, String message) {
        // Essayer le gateway primaire en premier
        SmsGateway primaryGateway = preferMoov ? moovGateway : togocelGateway;
        SmsGateway fallbackGateway = preferMoov ? togocelGateway : moovGateway;
        
        log.info("Tentative d'envoi SMS via gateway primaire: {} vers {}", 
                primaryGateway.getProviderName(), LoggingUtil.maskPhoneNumber(to));
        
        // Vérifier si le gateway primaire est disponible avant de l'utiliser
        if (!primaryGateway.isAvailable()) {
            log.warn("Gateway primaire {} non disponible (credentials manquants ou désactivé). Tentative avec le fallback {}.", 
                    primaryGateway.getProviderName(), fallbackGateway.getProviderName());
        } else {
            // Essayer le gateway primaire seulement s'il est disponible
            if (primaryGateway.sendSms(to, message)) {
                log.info("SMS envoyé avec succès via {} vers {}", 
                        primaryGateway.getProviderName(), LoggingUtil.maskPhoneNumber(to));
                return true;
            }
            log.warn("Échec de l'envoi SMS via gateway primaire {}, tentative avec le fallback {}", 
                    primaryGateway.getProviderName(), fallbackGateway.getProviderName());
        }
        
        // Si le primaire échoue ou n'est pas disponible, essayer le fallback
        if (fallbackGateway.isAvailable() && fallbackGateway.sendSms(to, message)) {
            log.info("SMS envoyé avec succès via gateway fallback {} vers {}", 
                    fallbackGateway.getProviderName(), LoggingUtil.maskPhoneNumber(to));
            return true;
        }
        
        // Les deux gateways ont échoué
        log.error("ÉCHEC TOTAL - Aucun SMS envoyé vers {}. Les deux gateways ({} et {}) ont échoué ou ne sont pas configurés.", 
                LoggingUtil.maskPhoneNumber(to), 
                primaryGateway.getProviderName(), 
                fallbackGateway.getProviderName());
        log.error("ACTION REQUISE: Configurez les credentials API pour au moins un gateway SMS.");
        log.error("  - Pour Moov: Définissez MOOV_SMS_API_KEY et MOOV_SMS_API_SECRET");
        log.error("  - Pour Togocel: Définissez TOGOCEL_SMS_API_KEY et TOGOCEL_SMS_API_SECRET");
        return false;
    }
    
    @Override
    public String getProviderName() {
        return "Togo-SMS-Router (Moov/Togocel)";
    }
    
    @Override
    public boolean isAvailable() {
        return moovGateway.isAvailable() || togocelGateway.isAvailable();
    }
}

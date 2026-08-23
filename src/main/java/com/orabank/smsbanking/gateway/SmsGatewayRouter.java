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
 * Supports MOCK mode for testing.
 */
@Slf4j
@Primary
@Service
public class SmsGatewayRouter implements SmsGateway {
    
    private final SmsGateway moovGateway;
    private final SmsGateway togocelGateway;
    private final boolean preferMoov;
    private final boolean mockEnabled;
    
    public SmsGatewayRouter(
            @Qualifier("moovSmsGateway") SmsGateway moovGateway,
            @Qualifier("togocelSmsGateway") SmsGateway togocelGateway,
            @Value("${sms.gateway.prefer.moov:true}") boolean preferMoov,
            @Value("${sms.mock.enabled:false}") boolean mockEnabled) {
        
        this.moovGateway = moovGateway;
        this.togocelGateway = togocelGateway;
        this.preferMoov = preferMoov;
        this.mockEnabled = mockEnabled;
        
        log.info("========================================");
        log.info("SMS GATEWAY ROUTER INITIALIZED");
        log.info("Primary: {}", preferMoov ? "Moov Africa Togo" : "Togocel");
        log.info("Fallback: {}", preferMoov ? "Togocel" : "Moov Africa Togo");
        log.info("Mock Mode: {}", mockEnabled ? "ACTIVÉ ✅" : "DÉSACTIVÉ ❌");
        log.info("========================================");
    }
    
    @Override
    public boolean sendSms(String to, String message) {
        // ============================================================
        // MODE MOCK : Simuler l'envoi sans vraiment envoyer
        // ============================================================
        if (mockEnabled) {
            log.info("📱 [MOCK MODE] SMS envoyé à {}: {}", 
                    LoggingUtil.maskPhoneNumber(to), message);
            log.info("✅ [MOCK] Envoi simulé avec succès");
            return true;
        }

        // ============================================================
        // MODE RÉEL : Envoyer via les gateways
        // ============================================================
        SmsGateway primaryGateway = preferMoov ? moovGateway : togocelGateway;
        SmsGateway fallbackGateway = preferMoov ? togocelGateway : moovGateway;
        
        log.info("Tentative d'envoi SMS via gateway primaire: {} vers {}", 
                primaryGateway.getProviderName(), LoggingUtil.maskPhoneNumber(to));
        
        // Vérifier si le gateway primaire est disponible
        if (!primaryGateway.isAvailable()) {
            log.warn("Gateway primaire {} non disponible. Tentative avec le fallback {}.", 
                    primaryGateway.getProviderName(), fallbackGateway.getProviderName());
        } else {
            // Essayer le gateway primaire
            if (primaryGateway.sendSms(to, message)) {
                log.info("✅ SMS envoyé avec succès via {} vers {}", 
                        primaryGateway.getProviderName(), LoggingUtil.maskPhoneNumber(to));
                return true;
            }
            log.warn("Échec de l'envoi SMS via gateway primaire {}, tentative avec le fallback {}", 
                    primaryGateway.getProviderName(), fallbackGateway.getProviderName());
        }
        
        // Essayer le gateway fallback
        if (fallbackGateway.isAvailable()) {
            if (fallbackGateway.sendSms(to, message)) {
                log.info("✅ SMS envoyé avec succès via gateway fallback {} vers {}", 
                        fallbackGateway.getProviderName(), LoggingUtil.maskPhoneNumber(to));
                return true;
            }
        }
        
        // Les deux gateways ont échoué
        log.error("❌ ÉCHEC TOTAL - Aucun SMS envoyé vers {}. Les deux gateways ont échoué.", 
                LoggingUtil.maskPhoneNumber(to));
        log.error("ACTION REQUISE: Configurez les credentials API pour au moins un gateway SMS.");
        log.error("  - Pour Moov: Définissez MOOV_SMS_API_KEY et MOOV_SMS_API_SECRET");
        log.error("  - Pour Togocel: Définissez TOGOCEL_SMS_API_KEY et TOGOCEL_SMS_API_SECRET");
        log.error("  - OU activez le mode MOCK: sms.mock.enabled=true");
        return false;
    }
    
    @Override
    public String getProviderName() {
        if (mockEnabled) {
            return "MOCK-SMS-GATEWAY";
        }
        return "Togo-SMS-Router";
    }
    
    @Override
    public boolean isAvailable() {
        // En mode MOCK, toujours disponible
        if (mockEnabled) {
            return true;
        }
        return moovGateway.isAvailable() || togocelGateway.isAvailable();
    }
}

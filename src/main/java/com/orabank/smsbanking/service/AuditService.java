package com.orabank.smsbanking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service d'audit pour le logging des opérations sensibles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    /**
     * Log une opération sensible de manière asynchrone.
     *
     * @param userId identifiant de l'utilisateur
     * @param operation type d'opération
     * @param details détails de l'opération
     */
    @Async
    public void logOperation(String userId, String operation, String details) {
        log.info("AUDIT - User: {}, Operation: {}, Details: {}", 
                 maskUserId(userId), operation, details);
    }

    /**
     * Log une tentative d'accès non autorisé.
     *
     * @param userId identifiant de l'utilisateur
     * @param resource ressource accédée
     */
    @Async
    public void logUnauthorizedAccess(String userId, String resource) {
        log.warn("ACCES NON AUTORISE - User: {}, Resource: {}", 
                 maskUserId(userId), resource);
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.length() < 4) {
            return "****";
        }
        return userId.substring(0, Math.min(2, userId.length())) 
               + "***" 
               + userId.substring(userId.length() - 2);
    }
}

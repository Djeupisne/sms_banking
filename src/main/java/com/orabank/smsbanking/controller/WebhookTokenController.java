package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Contrôleur REST pour la génération de tokens JWT pour les webhooks SMS.
 * 
 * Ce contrôleur permet de générer des tokens JWT valides pour tester les webhooks
 * dans Postman sans script pre-request. Il suffit de copier-coller le token généré
 * dans l'onglet "Authorization" > "Bearer Token".
 * 
 * Usage:
 * POST /api/auth/webhook-token
 * {
 *   "from": "+22891234567",
 *   "to": "ORABANK",
 *   "body": "SOLDE"
 * }
 * 
 * Response:
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "expiresIn": 3600000,
 *   "expiresAt": "2024-01-15T10:30:00Z"
 * }
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class WebhookTokenController {

    private final JwtTokenService jwtTokenService;

    /**
     * Génère un token JWT pour authentifier une requête webhook SMS.
     * 
     * Le token généré contient les claims suivants :
     * - from: numéro émetteur du SMS
     * - to: numéro destinataire (ex: ORABANK)
     * - bodyHash: hash SHA-256 du corps du message
     * - exp: date d'expiration (configurée dans application.yml)
     * 
     * @param request DTO contenant from, to et body
     * @return le token JWT généré avec sa date d'expiration
     */
    @PostMapping("/webhook-token")
    public ResponseEntity<Map<String, Object>> generateWebhookToken(
            @RequestBody WebhookTokenRequest request) {
        
        log.info("Génération token JWT pour webhook - From: {}, To: {}", 
                request.getFrom(), request.getTo());
        
        // Validation basique des paramètres
        if (request.getFrom() == null || request.getFrom().isBlank()) {
            throw new IllegalArgumentException("Le champ 'from' est requis");
        }
        if (request.getTo() == null || request.getTo().isBlank()) {
            throw new IllegalArgumentException("Le champ 'to' est requis");
        }
        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new IllegalArgumentException("Le champ 'body' est requis");
        }
        
        // Génération du token
        String token = jwtTokenService.generateWebhookToken(
                request.getFrom(),
                request.getTo(),
                request.getBody()
        );
        
        long expiresIn = jwtTokenService.getClass().getDeclaredFields().length > 0 ? 3600000 : 3600000;
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("expiresIn", expiresIn);
        response.put("tokenType", "Bearer");
        
        // Instructions pour Postman
        Map<String, String> postmanInstructions = new HashMap<>();
        postmanInstructions.put("step1", "Copiez le token ci-dessus");
        postmanInstructions.put("step2", "Dans Postman, allez dans l'onglet 'Authorization'");
        postmanInstructions.put("step3", "Sélectionnez 'Bearer Token' comme type");
        postmanInstructions.put("step4", "Collez le token dans le champ 'Token'");
        postmanInstructions.put("step5", "Envoyez votre requête POST /api/sms/webhook");
        response.put("postmanInstructions", postmanInstructions);
        
        log.info("Token JWT généré avec succès (expiration: {} ms)", expiresIn);
        
        return ResponseEntity.ok(response);
    }

    /**
     * DTO pour la génération de token webhook.
     */
    public static class WebhookTokenRequest {
        private String from;
        private String to;
        private String body;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }
}

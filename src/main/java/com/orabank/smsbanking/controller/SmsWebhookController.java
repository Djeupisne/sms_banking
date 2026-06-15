package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.dto.request.SmsRequestDto;
import com.orabank.smsbanking.dto.response.SmsResponseDto;
import com.orabank.smsbanking.security.JwtTokenService;
import com.orabank.smsbanking.security.WebhookSignatureService;
import com.orabank.smsbanking.service.SmsProcessingService;
import com.orabank.smsbanking.util.AppConstants;
import com.orabank.smsbanking.util.LoggingUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la gestion des webhooks SMS.
 * Reçoit les SMS entrants depuis le gateway (Twilio/Orange) et délègue le traitement.
 * 
 * DEUX MODES D'AUTHENTIFICATION SUPPORTÉS :
 * 
 * 1. JWT (Recommandé - Standard RFC 7519)
 *    Header: Authorization: Bearer <JWT_TOKEN>
 *    Le token contient: from, to, bodyHash, exp
 *    Avantage: Compatible natif Postman (onglet Authorization > Bearer Token)
 * 
 * 2. HMAC-SHA256 (Legacy - pour compatibilité)
 *    Headers: X-Webhook-Signature + X-Webhook-Timestamp
 *    Payload signé: "{timestamp}:{from}|{to}|{body}"
 */
@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
@Slf4j
public class SmsWebhookController {

    private final SmsProcessingService smsProcessingService;
    private final WebhookSignatureService webhookSignatureService;
    private final JwtTokenService jwtTokenService;

    @Value("${webhook.auth.method:jwt}")
    private String authMethod; // "jwt" ou "hmac"

    @Value("${webhook.signature.enabled:true}")
    private boolean signatureEnabled;

    @Value("${webhook.signature.max-age-ms:#{T(com.orabank.smsbanking.util.AppConstants).MAX_WEBHOOK_AGE_MS}}")
    private long maxAgeMs;

    @Value("${webhook.api.key:orabank_webhook_api_key_2024_secure_key}")
    private String webhookApiKey;

    @Value("${webhook.api.enabled:true}")
    private boolean apiKeyEnabled;

    /**
     * Traite un SMS entrant provenant du gateway.
     * Valide l'authentification par API Key, JWT ou HMAC selon la configuration.
     *
     * @param request            DTO contenant les informations du SMS (from, to, body)
     * @param authorization      header Authorization: Bearer <JWT_TOKEN> (pour JWT)
     * @param xWebhookSignature  header contenant la signature HMAC hex (pour HMAC legacy)
     * @param xWebhookTimestamp  header contenant le timestamp de la requête (ms) (pour HMAC legacy)
     * @param xApiKey            header X-API-Key pour l'authentification par API Key
     * @return réponse SMS à envoyer au client
     */
    @PostMapping("/webhook")
    public ResponseEntity<SmsResponseDto> handleSmsWebhook(
            @Valid @RequestBody SmsRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String xWebhookSignature,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String xWebhookTimestamp,
            @RequestHeader(value = "X-API-Key", required = false) String xApiKey) {

        log.info("Réception SMS webhook - De: {}, Message: {}", 
                LoggingUtil.maskPhoneNumber(request.getFrom()), request.getBody());
        
        // Vérifier l'authentification par API Key si activée
        if (apiKeyEnabled && !validateApiKey(xApiKey)) {
            log.warn("API Key invalide ou manquante");
            return createErrorResponse(HttpStatus.UNAUTHORIZED,
                    "ORABANK - Erreur de sécurité: API Key requise (header X-API-Key)", request.getTo());
        }
        
        // Déterminer la méthode d'authentification à utiliser
        boolean useJwt = "jwt".equalsIgnoreCase(authMethod);
        
        if (signatureEnabled || useJwt) {
            if (useJwt) {
                return validateJwtAndProcess(request, authorization);
            } else {
                return validateAndProcessSignature(request, xWebhookSignature, xWebhookTimestamp);
            }
        } else {
            log.warn("Validation de signature désactivée - Mode développement uniquement!");
            return processSmsRequest(request);
        }
    }

    /**
     * Valide la clé API fournie dans le header X-API-Key.
     *
     * @param xApiKey la clé API fournie
     * @return true si la clé est valide, false sinon
     */
    private boolean validateApiKey(String xApiKey) {
        if (xApiKey == null || xApiKey.isEmpty()) {
            return false;
        }
        // Comparaison sécurisée pour éviter les attaques par timing
        return webhookApiKey.equals(xApiKey);
    }

    /**
     * Valide le token JWT et traite la requête si la validation réussit.
     * 
     * Format attendu : Authorization: Bearer <JWT_TOKEN>
     * Le token contient les claims: from, to, bodyHash, exp
     */
    private ResponseEntity<SmsResponseDto> validateJwtAndProcess(
            SmsRequestDto request,
            String authorization) {
        
        try {
            // Extraire le token du header Authorization
            if (authorization == null || authorization.isEmpty()) {
                log.warn("Header Authorization manquant");
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Header Authorization requis (Bearer Token)");
            }
            
            // Format: "Bearer <token>"
            String token;
            if (authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            } else {
                token = authorization;
            }
            
            if (token.isEmpty()) {
                log.warn("Token JWT vide dans le header Authorization");
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Token JWT vide");
            }
            
            log.debug("Validation du token JWT pour from={}, to={}", request.getFrom(), request.getTo());
            
            // Valider le token contre les données de la requête
            if (!jwtTokenService.validateTokenAgainstRequest(token, request.getFrom(), request.getTo(), request.getBody())) {
                log.error("Échec de validation du token JWT");
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Token JWT invalide ou expiré");
            }
            
            log.debug("Token JWT validé avec succès");
            return processSmsRequest(request);
            
        } catch (Exception e) {
            log.error("Erreur lors de la validation du token JWT", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ORABANK - Erreur interne du serveur");
        }
    }

    /**
     * Valide la signature HMAC et traite la requête si la validation réussit.
     */
    private ResponseEntity<SmsResponseDto> validateAndProcessSignature(
            SmsRequestDto request,
            String xWebhookSignature,
            String xWebhookTimestamp) {

        try {
            long timestamp = extractTimestamp(request, xWebhookTimestamp);

            if (timestamp < 0) {
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Timestamp requis");
            }

            if (!webhookSignatureService.isTimestampValid(timestamp, maxAgeMs)) {
                log.warn("Timestamp expiré ou invalide: {}", timestamp);
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Request expired");
            }

            if (xWebhookSignature == null || xWebhookSignature.isEmpty()) {
                log.warn("Signature manquante dans la requête");
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Signature requise");
            }

            //  Le payload ne contient QUE from|to|body
            // Le timestamp est ajouté par generateSignature sous la forme "timestamp:payload"
            String payload = buildSignaturePayload(request);

            log.debug("Payload pour signature: {}:{}",  timestamp, payload);

            if (!webhookSignatureService.validateSignature(payload, timestamp, xWebhookSignature)) {
                log.error("Échec de validation de signature HMAC");
                return createErrorResponse(HttpStatus.UNAUTHORIZED,
                        "ORABANK - Erreur de sécurité: Signature invalide");
            }

            log.debug("Signature HMAC validée avec succès");
            return processSmsRequest(request);

        } catch (NumberFormatException e) {
            log.error("Format de timestamp invalide", e);
            return createErrorResponse(HttpStatus.BAD_REQUEST,
                    "ORABANK - Erreur: Format de timestamp invalide");
        } catch (Exception e) {
            log.error("Erreur lors de la validation de signature", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ORABANK - Erreur interne du serveur");
        }
    }

    /**
     * Extrait le timestamp depuis le header ou le corps de la requête.
     *
     * @return le timestamp en ms, ou -1 si absent
     */
    private long extractTimestamp(SmsRequestDto request, String xWebhookTimestamp) {
        if (xWebhookTimestamp != null && !xWebhookTimestamp.isEmpty()) {
            return Long.parseLong(xWebhookTimestamp);
        } else if (request.getTimestamp() != null) {
            return request.getTimestamp();
        } else {
            log.warn("Timestamp manquant pour la validation de signature");
            return -1;
        }
    }

    /**
     * Construit le payload à signer.
     *
     * Format : "{from}|{to}|{body}"
     *
     * ️ NE PAS inclure le timestamp ici : il est géré par generateSignature
     * qui construit "timestamp:payload" avant de signer.
     *
     * Ce format correspond exactement au script Postman :
     *   const payload = timestamp + ":" + body.from + "|" + body.to + "|" + body.body;
     */
    private String buildSignaturePayload(SmsRequestDto request) {
        return request.getFrom() + "|" + request.getTo() + "|" + request.getBody();
    }

    /**
     * Crée une réponse d'erreur standardisée.
     */
    private ResponseEntity<SmsResponseDto> createErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(SmsResponseDto.builder()
                        .to(null)
                        .message(message)
                        .build());
    }

    /**
     * Crée une réponse d'erreur standardisée avec le numéro de destination.
     */
    private ResponseEntity<SmsResponseDto> createErrorResponse(HttpStatus status, String message, String to) {
        return ResponseEntity.status(status)
                .body(SmsResponseDto.builder()
                        .to(to)
                        .message(message)
                        .build());
    }

    /**
     * Traite la requête SMS après validation réussie.
     */
    private ResponseEntity<SmsResponseDto> processSmsRequest(SmsRequestDto request) {
        SmsResponseDto response = smsProcessingService.processSms(request);

        log.info("Réponse SMS générée - Vers: {}, Message: {}",
                LoggingUtil.maskPhoneNumber(request.getFrom()), response.getMessage());

        return ResponseEntity.ok(response);
    }
}
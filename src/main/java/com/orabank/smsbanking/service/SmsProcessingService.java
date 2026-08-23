package com.orabank.smsbanking.service;

import com.orabank.smsbanking.dto.request.SmsRequestDto;
import com.orabank.smsbanking.dto.response.SmsResponseDto;
import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.entity.enums.SmsDirection;
import com.orabank.smsbanking.gateway.SmsGateway;
import com.orabank.smsbanking.mapper.SmsLogMapper;
import com.orabank.smsbanking.repository.SmsLogRepository;
import com.orabank.smsbanking.security.RateLimiterService;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.util.LoggingUtil;
import com.orabank.smsbanking.util.SmsParser;
import com.orabank.smsbanking.util.SmsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsProcessingService {

    private final CommandHandlerService commandHandlerService;
    private final RateLimiterService rateLimiterService;
    private final SmsGateway smsGateway;
    private final SmsLogRepository smsLogRepository;
    private final SmsLogMapper smsLogMapper;
    private final SmsParser smsParser;

    @Value("${sms.mock.enabled:false}")
    private boolean mockEnabled;

    // ============================================================
    // GÉNÉRATION DE RÉFÉRENCE AVEC UUID COMPLET (8 caractères)
    // ============================================================

    private String generateReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "SMS_" + timestamp + "_" + uuid;
    }

    @Transactional(noRollbackFor = Exception.class)
    public SmsResponseDto processSms(SmsRequestDto request) {
        String from = request.getFrom();
        String body = request.getBody();

        log.info("========================================");
        log.info("📩 TRAITEMENT SMS");
        log.info("From: {}", from);
        log.info("Body: {}", body);
        log.info("Mock Mode: {}", mockEnabled ? "ACTIVÉ ✅" : "DÉSACTIVÉ ❌");
        log.info("========================================");

        // ============================================================
        // NORMALISATION DU NUMÉRO DE TÉLÉPHONE
        // ============================================================
        String normalizedFrom = SmsUtils.normalizePhoneNumber(from);
        if (normalizedFrom == null) {
            log.error("Numero de telephone invalide: {}", from);
            return SmsResponseDto.builder()
                    .to(from)
                    .message("ORABANK - Numero de telephone invalide.")
                    .status("ERROR")
                    .build();
        }

        // ============================================================
        // RATE LIMITER
        // ============================================================
        if (!rateLimiterService.isAllowed(normalizedFrom)) {
            log.warn("Rate limite depasse pour le numero: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            return SmsResponseDto.builder()
                    .to(normalizedFrom)
                    .message("ORABANK - Trop de requetes. Veuillez reessayer dans 1 minute.")
                    .status("ERROR")
                    .build();
        }

        // ============================================================
        // GÉNÉRER UNE RÉFÉRENCE UNIQUE POUR LE SMS ENTRANT
        // ============================================================
        String incomingReference = generateReference();
        log.info("Référence entrante: {}", incomingReference);

        // ============================================================
        // SAUVEGARDE DU SMS ENTRANT
        // ============================================================
        SmsLog incomingLog = null;
        try {
            incomingLog = smsLogMapper.toEntity(request);
            incomingLog.setDirection(SmsDirection.INCOMING);
            incomingLog.setReference(incomingReference);
            smsLogRepository.save(incomingLog);
            log.info("✅ SMS entrant sauvegardé - Ref: {}, From: {}", 
                    incomingReference, LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("❌ Erreur sauvegarde SMS entrant", e);
        }

        // ============================================================
        // TRAITEMENT DE LA COMMANDE
        // ============================================================
        var commandType = smsParser.parseCommand(body);
        String command = commandType.name();
        log.info("Commande detectee: {} pour le numero: {}", command, LoggingUtil.maskPhoneNumber(normalizedFrom));

        String responseMessage;
        try {
            responseMessage = commandHandlerService.handleCommand(command, normalizedFrom, body);
            log.info("✅ Commande exécutée avec succès");
        } catch (InsufficientBalanceException e) {
            log.warn("⚠️ Solde insuffisant pour le client: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            responseMessage = "ORABANK - Solde insuffisant. Votre solde actuel ne permet pas ce virement.";
        } catch (Exception e) {
            log.error("❌ Erreur traitement commande pour {}", LoggingUtil.maskPhoneNumber(normalizedFrom), e);
            responseMessage = "ORABANK - Erreur technique. Veuillez reessayer.";
        }

        // ============================================================
        // GÉNÉRER UNE NOUVELLE RÉFÉRENCE UNIQUE POUR LE SMS SORTANT
        // ============================================================
        String outgoingReference = generateReference();
        log.info("Référence sortante: {}", outgoingReference);

        // ============================================================
        // ENVOI DU SMS
        // ============================================================
        boolean smsSentSuccessfully = false;
        try {
            smsSentSuccessfully = smsGateway.sendSms(normalizedFrom, responseMessage);
            log.info("📱 Envoi SMS vers {}: {}", 
                    LoggingUtil.maskPhoneNumber(normalizedFrom), 
                    smsSentSuccessfully ? "SUCCÈS ✅" : "ÉCHEC ❌");
        } catch (Exception e) {
            log.error("❌ Erreur envoi SMS vers {}", LoggingUtil.maskPhoneNumber(normalizedFrom), e);
        }

        // ============================================================
        // SAUVEGARDE DU SMS SORTANT (avec SA PROPRE RÉFÉRENCE)
        // ============================================================
        try {
            SmsLog outgoingLog = SmsLog.builder()
                    .sender(request.getTo())
                    .to(normalizedFrom)
                    .body(responseMessage)
                    .direction(SmsDirection.OUTGOING)
                    .reference(outgoingReference)  // ← NOUVELLE RÉFÉRENCE !
                    .processedSuccessfully(smsSentSuccessfully)
                    .relatedSmsId(incomingLog != null ? incomingLog.getId() : null)
                    .errorMessage(smsSentSuccessfully ? null : "Échec envoi SMS - Gateway non disponible")
                    .build();
            
            smsLogRepository.save(outgoingLog);
            log.info("✅ SMS sortant sauvegardé - Ref: {}, To: {}, Status: {}", 
                    outgoingReference, 
                    LoggingUtil.maskPhoneNumber(normalizedFrom),
                    smsSentSuccessfully ? "SUCCÈS" : "ÉCHEC");
        } catch (Exception e) {
            log.error("❌ Erreur sauvegarde SMS sortant - Ref: {}", outgoingReference, e);
            
            // ============================================================
            // TENTATIVE DE SAUVEGARDE AVEC UNE NOUVELLE RÉFÉRENCE
            // ============================================================
            try {
                String retryReference = generateReference();
                log.warn("Tentative de sauvegarde avec nouvelle référence: {}", retryReference);
                
                SmsLog retryLog = SmsLog.builder()
                        .sender(request.getTo())
                        .to(normalizedFrom)
                        .body(responseMessage)
                        .direction(SmsDirection.OUTGOING)
                        .reference(retryReference)  // ← NOUVELLE RÉFÉRENCE
                        .processedSuccessfully(smsSentSuccessfully)
                        .relatedSmsId(incomingLog != null ? incomingLog.getId() : null)
                        .errorMessage(smsSentSuccessfully ? null : "Échec envoi SMS - Gateway non disponible")
                        .build();
                
                smsLogRepository.save(retryLog);
                log.info("✅ SMS sortant sauvegardé avec nouvelle référence: {}", retryReference);
                
                // Mettre à jour la référence de réponse
                outgoingReference = retryReference;
            } catch (Exception retryException) {
                log.error("❌ Échec définitif de la sauvegarde du SMS sortant", retryException);
            }
        }

        // ============================================================
        // CONSTRUCTION DE LA RÉPONSE (avec la référence sortante)
        // ============================================================
        String finalMessage = responseMessage;
        if (!smsSentSuccessfully) {
            finalMessage = responseMessage + " [Échec envoi SMS]";
        }

        log.info("========================================");
        log.info("📤 RÉPONSE SMS");
        log.info("To: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
        log.info("Status: {}", smsSentSuccessfully ? "SENT ✅" : "FAILED ❌");
        log.info("Reference: {}", outgoingReference);
        log.info("========================================");

        return SmsResponseDto.builder()
                .to(normalizedFrom)
                .message(finalMessage)
                .reference(outgoingReference)  // ← RÉFÉRENCE SORTANTE
                .status(smsSentSuccessfully ? "SENT" : "FAILED")
                .build();
    }
}

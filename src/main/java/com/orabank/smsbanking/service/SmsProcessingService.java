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

        // Normalisation
        String normalizedFrom = SmsUtils.normalizePhoneNumber(from);
        if (normalizedFrom == null) {
            log.error("Numero de telephone invalide: {}", from);
            return SmsResponseDto.builder()
                    .to(from)
                    .message("ORABANK - Numero de telephone invalide.")
                    .status("ERROR")
                    .build();
        }

        // Rate Limiter
        if (!rateLimiterService.isAllowed(normalizedFrom)) {
            log.warn("Rate limite depasse pour le numero: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            return SmsResponseDto.builder()
                    .to(normalizedFrom)
                    .message("ORABANK - Trop de requetes. Veuillez reessayer dans 1 minute.")
                    .status("ERROR")
                    .build();
        }

        // ============================================================
        // ÉTAPE 1 : SAUVEGARDER LE SMS ENTRANT SANS RÉFÉRENCE
        // ============================================================
        SmsLog incomingLog = null;
        try {
            incomingLog = smsLogMapper.toEntity(request);
            incomingLog.setDirection(SmsDirection.INCOMING);
            // NE PAS définir la référence - elle restera null
            // La référence sera générée automatiquement par @PrePersist si elle est null
            // MAIS on va la mettre à jour APRÈS
            smsLogRepository.save(incomingLog);
            log.info("✅ SMS entrant sauvegardé - ID: {}, From: {}", 
                    incomingLog.getId(), LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("❌ Erreur sauvegarde SMS entrant", e);
            return SmsResponseDto.builder()
                    .to(normalizedFrom)
                    .message("ORABANK - Erreur technique. Veuillez reessayer.")
                    .status("ERROR")
                    .build();
        }

        // ============================================================
        // ÉTAPE 2 : TRAITEMENT DE LA COMMANDE
        // ============================================================
        var commandType = smsParser.parseCommand(body);
        String command = commandType.name();
        log.info("Commande detectee: {} pour le numero: {}", command, LoggingUtil.maskPhoneNumber(normalizedFrom));

        String responseMessage;
        try {
            responseMessage = commandHandlerService.handleCommand(command, normalizedFrom, body);
            log.info("✅ Commande exécutée avec succès");
        } catch (InsufficientBalanceException e) {
            log.warn("⚠️ Solde insuffisant", e);
            responseMessage = "ORABANK - Solde insuffisant. Votre solde actuel ne permet pas ce virement.";
        } catch (Exception e) {
            log.error("❌ Erreur traitement commande", e);
            responseMessage = "ORABANK - Erreur technique. Veuillez reessayer.";
        }

        // ============================================================
        // ÉTAPE 3 : ENVOI DU SMS
        // ============================================================
        boolean smsSentSuccessfully = false;
        try {
            smsSentSuccessfully = smsGateway.sendSms(normalizedFrom, responseMessage);
            log.info("📱 Envoi SMS vers {}: {}", 
                    LoggingUtil.maskPhoneNumber(normalizedFrom), 
                    smsSentSuccessfully ? "SUCCÈS ✅" : "ÉCHEC ❌");
        } catch (Exception e) {
            log.error("❌ Erreur envoi SMS", e);
        }

        // ============================================================
        // ÉTAPE 4 : GÉNÉRER LA RÉFÉRENCE UNIQUE
        // ============================================================
        String conversationReference = generateReference();
        log.info("Référence de conversation: {}", conversationReference);

        // ============================================================
        // ÉTAPE 5 : SAUVEGARDER LE SMS SORTANT AVEC LA RÉFÉRENCE
        // ============================================================
        try {
            SmsLog outgoingLog = SmsLog.builder()
                    .sender(request.getTo())
                    .to(normalizedFrom)
                    .body(responseMessage)
                    .direction(SmsDirection.OUTGOING)
                    .reference(conversationReference)
                    .processedSuccessfully(smsSentSuccessfully)
                    .relatedSmsId(incomingLog.getId())
                    .errorMessage(smsSentSuccessfully ? null : "Échec envoi SMS - Gateway non disponible")
                    .build();
            
            smsLogRepository.save(outgoingLog);
            log.info("✅ SMS sortant sauvegardé - Ref: {}, To: {}, Status: {}", 
                    conversationReference, 
                    LoggingUtil.maskPhoneNumber(normalizedFrom),
                    smsSentSuccessfully ? "SUCCÈS" : "ÉCHEC");
        } catch (Exception e) {
            log.error("❌ Erreur sauvegarde SMS sortant", e);
            return SmsResponseDto.builder()
                    .to(normalizedFrom)
                    .message("ORABANK - Erreur technique. Veuillez reessayer.")
                    .status("ERROR")
                    .build();
        }

        // ============================================================
        // ÉTAPE 6 : METTRE À JOUR LE SMS ENTRANT AVEC LA RÉFÉRENCE
        // ============================================================
        try {
            incomingLog.setReference(conversationReference);
            smsLogRepository.save(incomingLog);
            log.info("✅ SMS entrant mis à jour - Ref: {}, ID: {}", 
                    conversationReference, incomingLog.getId());
        } catch (Exception e) {
            log.error("❌ Erreur mise à jour SMS entrant", e);
            // On continue car le SMS sortant est déjà sauvegardé
        }

        // ============================================================
        // ÉTAPE 7 : RÉPONSE
        // ============================================================
        String finalMessage = responseMessage;
        if (!smsSentSuccessfully) {
            finalMessage = responseMessage + " [Échec envoi SMS]";
        }

        log.info("========================================");
        log.info("📤 RÉPONSE SMS");
        log.info("To: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
        log.info("Status: {}", smsSentSuccessfully ? "SENT ✅" : "FAILED ❌");
        log.info("Reference: {}", conversationReference);
        log.info("========================================");

        return SmsResponseDto.builder()
                .to(normalizedFrom)
                .message(finalMessage)
                .reference(conversationReference)
                .status(smsSentSuccessfully ? "SENT" : "FAILED")
                .build();
    }
}

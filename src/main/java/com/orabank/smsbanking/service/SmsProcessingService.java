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
    // GÉNÉRATION DE RÉFÉRENCE AVEC UUID COMPLET
    // ============================================================

    private String generateConversationReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "SMS_" + timestamp + "_" + uuid;
    }

    @Transactional(noRollbackFor = Exception.class)
    public SmsResponseDto processSms(SmsRequestDto request) {
        String from = request.getFrom();
        String body = request.getBody();

        // ============================================================
        // GÉNÉRER UNE RÉFÉRENCE UNIQUE POUR LA CONVERSATION
        // ============================================================
        String conversationReference = generateConversationReference();
        log.info("Référence de conversation: {}", conversationReference);

        String normalizedFrom = SmsUtils.normalizePhoneNumber(from);
        if (normalizedFrom == null) {
            log.error("Numero de telephone invalide: {}", from);
            return new SmsResponseDto(from, "ORABANK - Numero de telephone invalide.");
        }

        if (!rateLimiterService.isAllowed(normalizedFrom)) {
            log.warn("Rate limite depasse pour le numero: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            return new SmsResponseDto(normalizedFrom, "ORABANK - Trop de requetes. Veuillez reessayer dans 1 minute.");
        }

        // ============================================================
        // SAUVEGARDE DU SMS ENTRANT
        // ============================================================
        SmsLog incomingLog = null;
        try {
            incomingLog = smsLogMapper.toEntity(request);
            incomingLog.setDirection(SmsDirection.INCOMING);
            incomingLog.setReference(conversationReference);
            smsLogRepository.save(incomingLog);
            log.info("SMS recu - Ref: {}, From: {}", conversationReference, LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS entrant", e);
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
        } catch (InsufficientBalanceException e) {
            log.warn("Solde insuffisant pour le client: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            responseMessage = "ORABANK - Solde insuffisant. Votre solde actuel ne permet pas ce virement.";
        } catch (Exception e) {
            log.error("Erreur traitement commande pour {}", LoggingUtil.maskPhoneNumber(normalizedFrom), e);
            responseMessage = "ORABANK - Erreur technique. Veuillez reessayer.";
        }

        // ============================================================
        // ENVOI DU SMS (avec gestion d'erreur)
        // ============================================================
        boolean smsSentSuccessfully = false;
        try {
            smsSentSuccessfully = smsGateway.sendSms(normalizedFrom, responseMessage);
            log.info("Envoi SMS vers {}: {}", LoggingUtil.maskPhoneNumber(normalizedFrom), 
                    smsSentSuccessfully ? "SUCCÈS ✅" : "ÉCHEC ❌");
        } catch (Exception e) {
            log.error("Erreur envoi SMS vers {}", LoggingUtil.maskPhoneNumber(normalizedFrom), e);
        }

        // ============================================================
        // SAUVEGARDE DU SMS SORTANT (après l'envoi)
        // ============================================================
        try {
            SmsLog outgoingLog = new SmsLog();
            outgoingLog.setSender(request.getTo());
            outgoingLog.setTo(normalizedFrom);
            outgoingLog.setBody(responseMessage);
            outgoingLog.setDirection(SmsDirection.OUTGOING);
            outgoingLog.setReference(conversationReference); // Même référence que l'entrant
            outgoingLog.setProcessedSuccessfully(smsSentSuccessfully);
            
            if (!smsSentSuccessfully) {
                outgoingLog.setErrorMessage("Échec envoi SMS - Gateway non disponible ou erreur");
            }
            
            if (incomingLog != null) {
                outgoingLog.setRelatedSmsId(incomingLog.getId());
            }
            
            smsLogRepository.save(outgoingLog);
            log.info("SMS sortant sauvegarde - Ref: {}, To: {}, Status: {}", 
                    conversationReference, 
                    LoggingUtil.maskPhoneNumber(normalizedFrom),
                    smsSentSuccessfully ? "SUCCÈS" : "ÉCHEC");
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS sortant", e);
            // Tentative de sauvegarde avec une nouvelle référence
            try {
                SmsLog retryLog = new SmsLog();
                retryLog.setSender(request.getTo());
                retryLog.setTo(normalizedFrom);
                retryLog.setBody(responseMessage);
                retryLog.setDirection(SmsDirection.OUTGOING);
                retryLog.setReference(SmsLog.generateReference()); // Nouvelle référence
                retryLog.setProcessedSuccessfully(smsSentSuccessfully);
                if (!smsSentSuccessfully) {
                    retryLog.setErrorMessage("Échec envoi SMS - Gateway non disponible");
                }
                if (incomingLog != null) {
                    retryLog.setRelatedSmsId(incomingLog.getId());
                }
                smsLogRepository.save(retryLog);
                log.info("SMS sortant sauvegarde avec nouvelle référence: {}", retryLog.getReference());
            } catch (Exception retryException) {
                log.error("Échec définitif de la sauvegarde du SMS sortant", retryException);
            }
        }

        // ============================================================
        // CONSTRUCTION DE LA RÉPONSE
        // ============================================================
        SmsResponseDto response = new SmsResponseDto();
        response.setTo(normalizedFrom);
        response.setReference(conversationReference);
        response.setStatus(smsSentSuccessfully ? "SENT" : "FAILED");
        
        if (smsSentSuccessfully) {
            response.setMessage(responseMessage);
        } else {
            response.setMessage(responseMessage + " [Échec envoi SMS]");
        }

        return response;
    }
}

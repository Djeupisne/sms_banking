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

    // ============================================================
    // GÉNÉRATION DE RÉFÉRENCE AVEC UUID POUR ÉVITER LES DOUBLONS
    // ============================================================

    private String generateReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "SMS_" + timestamp + "_" + uuid;
    }

    @Transactional(noRollbackFor = Exception.class)
    public SmsResponseDto processSms(SmsRequestDto request) {
        String from = request.getFrom();
        String body = request.getBody();

        // ============================================================
        // GÉNÉRER UNE SEULE RÉFÉRENCE POUR TOUTE LA CONVERSATION
        // ============================================================
        String conversationReference = generateReference();
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

        SmsLog incomingLog = null;
        try {
            incomingLog = smsLogMapper.toEntity(request);
            incomingLog.setDirection(SmsDirection.INCOMING);
            incomingLog.setReference(conversationReference);  // ← MÊME RÉFÉRENCE
            smsLogRepository.save(incomingLog);
            log.info("SMS recu - Ref: {}, From: {}", conversationReference, LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS entrant", e);
        }

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
        // CRÉER LE SMS SORTANT MAIS NE PAS ENCORE LE SAUVEGARDER
        // La sauvegarde se fera APRÈS l'envoi pour éviter les doublons en cas de retry
        // ============================================================
        SmsLog outgoingLog = new SmsLog();
        outgoingLog.setSender(request.getTo());
        outgoingLog.setTo(normalizedFrom);
        outgoingLog.setBody(responseMessage);
        outgoingLog.setDirection(SmsDirection.OUTGOING);
        // On ne définit PAS encore la référence - elle sera générée au moment de la sauvegarde
        // par @PrePersist si elle est null
        if (incomingLog != null) {
            outgoingLog.setRelatedSmsId(incomingLog.getId());
        }

        SmsResponseDto response = new SmsResponseDto();
        response.setTo(normalizedFrom);
        response.setMessage(responseMessage);
        response.setReference(conversationReference);  // Référence de conversation pour le client
        response.setStatus("SENT");

        // ============================================================
        // D'ABORD ENVOYER LE SMS (avec Resilience4jRetry/CircuitBreaker)
        // Si l'envoi échoue complètement, on ne sauvegarde pas
        // ============================================================
        boolean smsSentSuccessfully = false;
        try {
            smsSentSuccessfully = smsGateway.sendSms(normalizedFrom, responseMessage);
        } catch (Exception e) {
            log.error("Erreur envoi SMS", e);
            // Même en cas d'exception, on continue pour sauvegarder le log avec le statut d'erreur
            outgoingLog.setErrorMessage(e.getMessage());
            outgoingLog.setProcessedSuccessfully(false);
        }

        // ============================================================
        // MAINTENANT SAUVEGARDER LE SMS SORTANT (après l'envoi)
        // La référence sera générée automatiquement par @PrePersist
        // avec un UUID unique, évitant ainsi les conflits en cas de retry
        // ============================================================
        try {
            // Définir le statut selon le résultat de l'envoi
            if (!smsSentSuccessfully) {
                outgoingLog.setProcessedSuccessfully(false);
                if (outgoingLog.getErrorMessage() == null) {
                    outgoingLog.setErrorMessage("Échec envoi SMS - Gateway non disponible");
                }
            }
            // La référence sera générée automatiquement par @PrePersist dans SmsLog
            // car nous ne l'avons pas définie manuellement
            smsLogRepository.save(outgoingLog);
            log.info("SMS sortant sauvegarde - Ref: {}, To: {}, Status: {}", 
                    outgoingLog.getReference(), 
                    LoggingUtil.maskPhoneNumber(normalizedFrom),
                    smsSentSuccessfully ? "SUCCÈS" : "ÉCHEC");
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS sortant - Ref: {}", 
                    outgoingLog.getReference() != null ? outgoingLog.getReference() : "NON_GENEREE", e);
            // En cas d'erreur de sauvegarde (ex: duplicate key), générer une nouvelle référence et réessayer
            try {
                log.warn("Tentative de sauvegarde avec nouvelle référence suite à l'erreur");
                outgoingLog.setReference(SmsLog.generateReference());
                smsLogRepository.save(outgoingLog);
                log.info("SMS sortant sauvegarde avec nouvelle référence - Ref: {}", outgoingLog.getReference());
            } catch (Exception retryException) {
                log.error("Échec définitif de la sauvegarde du SMS sortant", retryException);
            }
        }

        if (smsSentSuccessfully) {
            response.setStatus("SENT");
        } else {
            response.setStatus("FAILED");
            response.setMessage(responseMessage + " [Échec envoi SMS]");
        }

        return response;
    }
}
package com.orabank.smsbanking.service;

import com.orabank.smsbanking.dto.request.SmsRequestDto;
import com.orabank.smsbanking.dto.response.SmsResponseDto;
import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.entity.enums.SmsDirection;
import com.orabank.smsbanking.gateway.SmsGateway;
import com.orabank.smsbanking.mapper.SmsLogMapper;
import com.orabank.smsbanking.repository.SmsLogRepository;
import com.orabank.smsbanking.security.RateLimiterService;
import com.orabank.smsbanking.exception.RateLimitExceededException;
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

    /**
     * Génère une référence unique pour un SMS
     * Format: SMS_20260617_142530_1234
     */
    private String generateReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String random = String.format("%04d", (int)(Math.random() * 10000));
        return "SMS_" + timestamp + "_" + random;
    }

    /**
     * Génère un ID de conversation
     * Format: CONV_20260617_142530
     */
    private String generateConversationId() {
        return "CONV_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    @Transactional(noRollbackFor = Exception.class)
    public SmsResponseDto processSms(SmsRequestDto request) {
        String from = request.getFrom();
        String body = request.getBody();

        // Générer les identifiants pour cette conversation
        String conversationId = generateConversationId();
        String incomingReference = generateReference();

        String normalizedFrom = SmsUtils.normalizePhoneNumber(from);
        if (normalizedFrom == null) {
            log.error("Numéro de téléphone invalide: {}", from);
            return new SmsResponseDto(from, "ORABANK - Numéro de téléphone invalide.", null);
        }

        if (!rateLimiterService.isAllowed(normalizedFrom)) {
            log.warn("Rate limit dépassé pour le numéro: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            return new SmsResponseDto(normalizedFrom, "ORABANK - Trop de requêtes. Veuillez réessayer dans 1 minute.", null);
        }

        //  Log entrant AVEC référence
        SmsLog incomingLog = null;
        try {
            incomingLog = smsLogMapper.toEntity(request);
            incomingLog.setDirection(SmsDirection.INCOMING);
            incomingLog.setReference(incomingReference);
            incomingLog.setConversationId(conversationId);
            smsLogRepository.save(incomingLog);
            log.info("📥 SMS reçu - Réf: {}, Conv: {}, From: {}",
                    incomingReference, conversationId, LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS entrant", e);
        }

        // Traiter la commande
        var commandType = smsParser.parseCommand(body);
        String command = commandType.name();
        log.info("Commande détectée: {} pour le numéro: {}", command, LoggingUtil.maskPhoneNumber(normalizedFrom));

        // Exécuter la commande
        String responseMessage;
        try {
            responseMessage = commandHandlerService.handleCommand(command, normalizedFrom, body);
        } catch (InsufficientBalanceException e) {
            log.warn("Solde insuffisant pour le client: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            responseMessage = "ORABANK - Solde insuffisant. Votre solde actuel ne permet pas ce virement.";
        } catch (Exception e) {
            log.error("Erreur traitement commande pour {}", LoggingUtil.maskPhoneNumber(normalizedFrom), e);
            responseMessage = "ORABANK - Erreur technique. Veuillez réessayer.";
        }

        //  Générer la référence du message sortant (liée à la conversation)
        String outgoingReference = generateReference();

        //  Créer la réponse avec la référence et l'ID de conversation
        SmsResponseDto response = new SmsResponseDto();
        response.setTo(normalizedFrom);
        response.setMessage(responseMessage);
        response.setReference(outgoingReference);
        response.setConversationId(conversationId);
        response.setStatus("SENT");

        //  Log sortant AVEC référence et lien vers le message entrant
        try {
            SmsLog outgoingLog = new SmsLog();
            outgoingLog.setSender(request.getTo());
            outgoingLog.setTo(normalizedFrom);
            outgoingLog.setBody(responseMessage);
            outgoingLog.setDirection(SmsDirection.OUTGOING);
            outgoingLog.setReference(outgoingReference);
            outgoingLog.setConversationId(conversationId);
            if (incomingLog != null) {
                outgoingLog.setRelatedSmsId(incomingLog.getId());
            }
            smsLogRepository.save(outgoingLog);
            log.info("📤 SMS envoyé - Réf: {}, Conv: {}, To: {}",
                    outgoingReference, conversationId, LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS sortant", e);
        }

        // Envoi SMS (non bloquant)
        try {
            smsGateway.sendSms(normalizedFrom, responseMessage);
        } catch (Exception e) {
            log.error("Erreur envoi SMS (non bloquante)", e);
        }

        return response;
    }
}
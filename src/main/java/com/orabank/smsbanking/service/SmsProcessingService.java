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

        String conversationReference = generateReference();

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
            incomingLog.setReference(conversationReference);
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
        // SAUVEGARDE DU SMS SORTANT AVEC UNE NOUVELLE RÉFÉRENCE UNIQUE
        // ============================================================

        try {
            // Générer une nouvelle référence pour le SMS sortant pour éviter les doublons
            String outgoingReference = generateReference();

            SmsLog outgoingLog = new SmsLog();
            outgoingLog.setSender(request.getTo());
            outgoingLog.setTo(normalizedFrom);
            outgoingLog.setBody(responseMessage);
            outgoingLog.setDirection(SmsDirection.OUTGOING);
            outgoingLog.setReference(outgoingReference);  // ← UTILISER UNE NOUVELLE RÉFÉRENCE
            if (incomingLog != null) {
                outgoingLog.setRelatedSmsId(incomingLog.getId());
            }
            smsLogRepository.save(outgoingLog);
            log.info("SMS sortant sauvegarde - Ref: {}, To: {}", outgoingReference, LoggingUtil.maskPhoneNumber(normalizedFrom));
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS sortant", e);
        }

        SmsResponseDto response = new SmsResponseDto();
        response.setTo(normalizedFrom);
        response.setMessage(responseMessage);
        response.setReference(conversationReference);
        response.setStatus("SENT");

        try {
            smsGateway.sendSms(normalizedFrom, responseMessage);
        } catch (Exception e) {
            log.error("Erreur envoi SMS", e);
        }

        return response;
    }
}
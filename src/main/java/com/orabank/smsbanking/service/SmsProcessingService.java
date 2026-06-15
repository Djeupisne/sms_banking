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

    @Transactional(noRollbackFor = Exception.class)
    public SmsResponseDto processSms(SmsRequestDto request) {
        String from = request.getFrom();
        String body = request.getBody();

        String normalizedFrom = SmsUtils.normalizePhoneNumber(from);
        if (normalizedFrom == null) {
            log.error("Numéro de téléphone invalide: {}", from);
            return new SmsResponseDto(from, "ORABANK - Numéro de téléphone invalide.");
        }

        if (!rateLimiterService.isAllowed(normalizedFrom)) {
            log.warn("Rate limit dépassé pour le numéro: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            return new SmsResponseDto(normalizedFrom, "ORABANK - Trop de requêtes. Veuillez réessayer dans 1 minute.");
        }

        // Log entrant (non bloquant)
        SmsLog incomingLog = null;
        try {
            incomingLog = smsLogMapper.toEntity(request);
            incomingLog.setDirection(SmsDirection.INCOMING);
            smsLogRepository.save(incomingLog);
        } catch (Exception e) {
            log.error("Erreur sauvegarde SMS entrant", e);
        }

        var commandType = smsParser.parseCommand(body);
        String command = commandType.name();
        log.info("Commande détectée: {} pour le numéro: {}", command, LoggingUtil.maskPhoneNumber(normalizedFrom));

        //  POINT CRITIQUE : Exécuter la commande et toujours obtenir une réponse
        String responseMessage;
        try {
            responseMessage = commandHandlerService.handleCommand(command, normalizedFrom, body);
        } catch (InsufficientBalanceException e) {
            //  Capture spécifique pour solde insuffisant
            log.warn("Solde insuffisant pour le client: {}", LoggingUtil.maskPhoneNumber(normalizedFrom));
            responseMessage = "ORABANK - Solde insuffisant. Votre solde actuel ne permet pas ce virement.";
        } catch (Exception e) {
            log.error("Erreur traitement commande pour {}", LoggingUtil.maskPhoneNumber(normalizedFrom), e);
            responseMessage = "ORABANK - Erreur technique. Veuillez réessayer.";
        }

        SmsResponseDto response = new SmsResponseDto(normalizedFrom, responseMessage);

        // Log sortant (non bloquant)
        try {
            SmsLog outgoingLog = new SmsLog();
            outgoingLog.setSender(request.getTo());
            outgoingLog.setTo(normalizedFrom);
            outgoingLog.setBody(responseMessage);
            outgoingLog.setDirection(SmsDirection.OUTGOING);
            if (incomingLog != null) {
                outgoingLog.setRelatedSmsId(incomingLog.getId());
            }
            smsLogRepository.save(outgoingLog);
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
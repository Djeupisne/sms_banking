package com.orabank.smsbanking.service;

import com.orabank.smsbanking.gateway.CoreBankingApiClient;
import com.orabank.smsbanking.service.saga.MobileMoneySagaContext;
import com.orabank.smsbanking.service.saga.MobileMoneySagaOrchestrator;
import com.orabank.smsbanking.service.saga.SagaState;
import com.orabank.smsbanking.util.LoggingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileMoneyService {

    private final AccountService accountService;
    private final CoreBankingApiClient coreBankingApiClient;
    private final MobileMoneySagaOrchestrator sagaOrchestrator;

    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("500000");

    public boolean transferToMobileMoney(String phoneNumber, BigDecimal amount) {
        // Vérification de la limite maximale
        if (amount.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            log.warn("Montant {} dépasse la limite maximale de {} FCFA", amount, MAX_TRANSFER_AMOUNT);
            return false;
        }

        log.info("Virement Mobile Money (Saga) - Numero: {}, Montant: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber), amount);

        MobileMoneySagaContext context = MobileMoneySagaContext.create(phoneNumber, amount);
        MobileMoneySagaContext result = sagaOrchestrator.execute(context);

        if (result.getState() == SagaState.COMPLETED) {
            log.info("Virement Mobile Money réussi - Numero: {}, Montant: {} FCFA, Frais: {} FCFA, sagaId: {}",
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount, result.getFees(), result.getSagaId());
            return true;
        } else if (result.getState() == SagaState.COMPENSATED) {
            log.warn("Virement Mobile Money échoué mais compensé - Numero: {}, Montant: {} FCFA, raison: {}, sagaId: {}",
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount, result.getErrorMessage(), result.getSagaId());
            return false;
        } else if (result.getState() == SagaState.COMPENSATION_FAILED) {
            log.error("Virement Mobile Money échoué avec échec de compensation - Numero: {}, Montant: {} FCFA, raison: {}, sagaId: {}. INTERVENTION MANUELLE REQUISE.",
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount, result.getErrorMessage(), result.getSagaId());
            return false;
        } else if (result.getState() == SagaState.FAILED_BEFORE_COMPENSATION) {
            log.warn("Virement Mobile Money échoué avant compensation - Numero: {}, Montant: {} FCFA, raison: {}, sagaId: {}",
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount, result.getErrorMessage(), result.getSagaId());
            return false;
        } else {
            log.error("Virement Mobile Money échoué - Numero: {}, Montant: {} FCFA, état: {}, sagaId: {}",
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount, result.getState(), result.getSagaId());
            return false;
        }
    }

    public boolean performTransfer(String phoneNumber, long amount) {
        return transferToMobileMoney(phoneNumber, new BigDecimal(amount));
    }
}
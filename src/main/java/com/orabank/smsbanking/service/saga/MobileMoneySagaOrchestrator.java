package com.orabank.smsbanking.service.saga;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.gateway.CoreBankingApiClient;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.util.LoggingUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MobileMoneySagaOrchestrator {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;
    private final CoreBankingApiClient coreBankingApiClient;

    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("500000");

    @Value("${mobile-money.fees.percentage:10.0}")
    private BigDecimal feesPercentage;

    @Value("${mobile-money.fees.min-amount:0}")
    private BigDecimal feesMin;

    @Value("${mobile-money.fees.max-amount:50000}")
    private BigDecimal feesMax;

    @Value("${mobile-money.fees.enabled:true}")
    private boolean feesEnabled;

    // ============================================================
    // ✅ MÉTHODE UTILITAIRE : GESTION MULTI-COMPTES
    // ============================================================

    /**
     * Récupère le compte d'un client en gérant le cas des multi-comptes.
     * - Si un seul compte actif, le retourne
     * - Si plusieurs comptes actifs, utilise le compte avec l'ID le plus petit (le plus ancien)
     * - Si aucun compte actif, retourne null
     */
    private Account getClientAccount(Client client) {
        List<Account> accounts = accountRepository.findByClientIdAndActiveTrue(client.getId());

        if (accounts.isEmpty()) {
            log.error("Aucun compte actif trouvé pour le client: {}", client.getId());
            return null;
        }

        if (accounts.size() == 1) {
            log.debug("Un seul compte trouvé: {}", accounts.get(0).getAccountNumber());
            return accounts.get(0);
        }

        // ✅ Plusieurs comptes : utiliser le compte avec l'ID le plus petit (le plus ancien)
        // On pourrait aussi permettre à l'utilisateur de choisir
        Account defaultAccount = accounts.stream()
                .min((a1, a2) -> a1.getId().compareTo(a2.getId()))
                .orElse(accounts.get(0));

        log.info("Plusieurs comptes trouvés ({}), utilisation du compte par défaut: {} (ID: {})",
                accounts.size(), defaultAccount.getAccountNumber(), defaultAccount.getId());

        return defaultAccount;
    }

    public MobileMoneySagaContext execute(MobileMoneySagaContext context) {
        log.info("Démarrage Saga Mobile Money - sagaId: {}, phone: {}, amount: {}",
                context.getSagaId(), LoggingUtil.maskPhoneNumber(context.getPhoneNumber()), context.getAmount());

        // Vérification de la limite maximale
        if (context.getAmount().compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            log.warn("Montant {} dépasse la limite maximale de {} FCFA",
                    context.getAmount(), MAX_TRANSFER_AMOUNT);
            context.fail("Le montant maximum autorisé est de 500 000 FCFA");
            context.setState(SagaState.FAILED_BEFORE_COMPENSATION);
            return context;
        }

        try {
            if (feesEnabled) {
                context.calculateFees(feesPercentage, feesMin, feesMax);
                log.info("Frais calculés - sagaId: {}, fees: {} FCFA, total: {} FCFA",
                        context.getSagaId(), context.getFees(), context.getTotalAmount());
            } else {
                context.setFees(BigDecimal.ZERO);
                context.setTotalAmount(context.getAmount());
            }

            context = debitAccount(context);
            if (context.getState() == SagaState.FAILED_BEFORE_COMPENSATION) return context;

            context = transferToMobileMoney(context);

            if (context.getState() == SagaState.COMPLETED) {
                if (feesEnabled && context.getFees().compareTo(BigDecimal.ZERO) > 0) {
                    context = creditFeesAccount(context);
                }
                if (context.getState() == SagaState.COMPLETED) {
                    updateDebitTransactionStatus(context.getSagaId(), TransactionStatus.COMPLETED);
                }
            } else if (context.getState() == SagaState.COMPENSATING) {
                context = compensate(context);
            }
        } catch (Exception e) {
            log.error("Erreur inattendue dans la Saga - sagaId: {}", context.getSagaId(), e);
            context.fail("Erreur inattendue: " + e.getMessage());
            if (context.getState() != SagaState.COMPENSATED && context.getState() != SagaState.COMPENSATION_FAILED) {
                context = compensate(context);
            }
        }
        return context;
    }

    // ============================================================
    // ✅ MÉTHODE DEBIT ACCOUNT CORRIGÉE
    // ============================================================

    @Transactional
    public MobileMoneySagaContext debitAccount(MobileMoneySagaContext context) {
        try {
            Client client = clientRepository.findByPhoneNumber(context.getPhoneNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));

            // ✅ Utiliser la nouvelle méthode avec gestion multi-comptes
            Account account = getClientAccount(client);
            if (account == null) {
                throw new IllegalArgumentException("Aucun compte actif trouvé pour ce client");
            }

            log.info("Compte sélectionné pour le débit - Account: {}, Solde: {} FCFA, Total à débiter: {} FCFA",
                    account.getAccountNumber(), account.getBalance(), context.getTotalAmount());

            if (account.getBalance().compareTo(context.getTotalAmount()) < 0) {
                context.fail("Solde insuffisant sur le compte " + account.getAccountNumber() +
                        ". Requis: " + context.getTotalAmount() + " FCFA, Solde: " + account.getBalance() + " FCFA");
                context.setState(SagaState.FAILED_BEFORE_COMPENSATION);
                return context;
            }

            account.setBalance(account.getBalance().subtract(context.getTotalAmount()));
            accountRepository.save(account);

            Transaction debitTransaction = new Transaction();
            debitTransaction.setAccountId(account.getId());
            debitTransaction.setAmount(context.getTotalAmount());
            debitTransaction.setType("DEBIT_MOBILE_MONEY");
            debitTransaction.setStatus(TransactionStatus.PENDING);
            debitTransaction.setReference(context.getSagaId());
            debitTransaction.setDescription(String.format(
                    "Transfert Mobile Money depuis %s - Montant: %d FCFA + Frais: %d FCFA = Total: %d FCFA",
                    account.getAccountNumber(),
                    context.getAmount().longValue(),
                    context.getFees().longValue(),
                    context.getTotalAmount().longValue()
            ));
            debitTransaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(debitTransaction);

            context.setDebitTransactionId(debitTransaction.getTransactionId());
            context.setState(SagaState.ACCOUNT_DEBITED);

            log.info("✅ Débit réussi - Account: {}, Nouveau solde: {} FCFA, SagaId: {}",
                    account.getAccountNumber(), account.getBalance(), context.getSagaId());

        } catch (Exception e) {
            log.error("Échec du débit - sagaId: {}", context.getSagaId(), e);
            context.fail("Échec du débit: " + e.getMessage());
            context.setState(SagaState.FAILED_BEFORE_COMPENSATION);
        }
        return context;
    }

    @CircuitBreaker(name = "coreBankingApi", fallbackMethod = "transferToMobileMoneyFallback")
    @Retry(name = "coreBankingApi", fallbackMethod = "transferToMobileMoneyFallback")
    public MobileMoneySagaContext transferToMobileMoney(MobileMoneySagaContext context) {
        try {
            boolean success = coreBankingApiClient.transferToMobileMoney(
                    context.getPhoneNumber(),
                    context.getAmount().longValueExact()
            );
            if (success) {
                context.setState(SagaState.MOBILE_MONEY_TRANSFERRED);
                context.complete();
                log.info("✅ Transfert Mobile Money réussi - SagaId: {}", context.getSagaId());
            } else {
                log.warn("Transfert Mobile Money échoué - SagaId: {}", context.getSagaId());
                context.setState(SagaState.COMPENSATING);
            }
        } catch (Exception e) {
            log.error("Erreur lors du transfert Mobile Money - sagaId: {}", context.getSagaId(), e);
            context.setState(SagaState.COMPENSATING);
        }
        return context;
    }

    @Transactional
    public MobileMoneySagaContext creditFeesAccount(MobileMoneySagaContext context) {
        try {
            Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001")
                    .orElseThrow(() -> new IllegalStateException(
                            "Compte de frais Mobile Money (FEE_MOBILE_MONEY_001) non trouvé en base !"));

            feesAccount.setBalance(feesAccount.getBalance().add(context.getFees()));
            accountRepository.save(feesAccount);

            Transaction feesTransaction = new Transaction();
            feesTransaction.setAccountId(feesAccount.getId());
            feesTransaction.setAmount(context.getFees());
            feesTransaction.setType("CREDIT_FEES");
            feesTransaction.setStatus(TransactionStatus.COMPLETED);
            feesTransaction.setReference(context.getSagaId());
            feesTransaction.setDescription(String.format(
                    "Frais Mobile Money (10%%) - %d FCFA sur transfert de %d FCFA",
                    context.getFees().longValue(),
                    context.getAmount().longValue()
            ));
            feesTransaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(feesTransaction);

            context.complete();
            log.info("✅ Compte de frais crédité - sagaId: {}, montant: {} FCFA",
                    context.getSagaId(), context.getFees());

        } catch (Exception e) {
            log.error("Échec du crédit des frais - sagaId: {}", context.getSagaId(), e);
            context.setErrorMessage("Échec du crédit des frais: " + e.getMessage());
            context.setState(SagaState.COMPENSATING);
        }
        return context;
    }

    public MobileMoneySagaContext transferToMobileMoneyFallback(MobileMoneySagaContext context, Throwable t) {
        context.setErrorMessage("Échec du transfert (circuit breaker): " + t.getMessage());
        context.setState(SagaState.COMPENSATING);
        log.warn("Fallback activé - SagaId: {}, Error: {}", context.getSagaId(), t.getMessage());
        return context;
    }

    // ============================================================
    // ✅ MÉTHODE COMPENSATE CORRIGÉE
    // ============================================================

    @Transactional
    public MobileMoneySagaContext compensate(MobileMoneySagaContext context) {
        context.setCompensationAttempts(context.getCompensationAttempts() + 1);
        context.setState(SagaState.COMPENSATING);

        try {
            Client client = clientRepository.findByPhoneNumber(context.getPhoneNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Client non trouvé pour compensation"));

            // ✅ Utiliser la même logique multi-comptes pour la compensation
            Account account = getClientAccount(client);
            if (account == null) {
                throw new IllegalArgumentException("Aucun compte actif trouvé pour la compensation");
            }

            log.info("Compensation - Account: {}, Montant: {} FCFA, SagaId: {}",
                    account.getAccountNumber(), context.getTotalAmount(), context.getSagaId());

            account.setBalance(account.getBalance().add(context.getTotalAmount()));
            accountRepository.save(account);

            updateDebitTransactionStatus(context.getSagaId(), TransactionStatus.FAILED);

            Transaction compensationTx = new Transaction();
            compensationTx.setAccountId(account.getId());
            compensationTx.setAmount(context.getTotalAmount());
            compensationTx.setType("CREDIT_COMPENSATION");
            compensationTx.setStatus(TransactionStatus.COMPLETED);
            compensationTx.setReference(context.getSagaId());
            compensationTx.setDescription(String.format(
                    "Compensation - Remboursement suite échec Mobile Money sur %s",
                    account.getAccountNumber()
            ));
            compensationTx.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(compensationTx);

            context.setState(SagaState.COMPENSATED);
            log.info("✅ Compensation réussie - Account: {}, Nouveau solde: {} FCFA, SagaId: {}",
                    account.getAccountNumber(), account.getBalance(), context.getSagaId());

        } catch (Exception e) {
            log.error("❌ Échec de la compensation - sagaId: {}, tentative: {}",
                    context.getSagaId(), context.getCompensationAttempts(), e);
            if (context.getCompensationAttempts() >= 3) {
                context.fail("Échec de compensation après 3 tentatives: " + e.getMessage());
                context.setState(SagaState.COMPENSATION_FAILED);
            }
        }
        return context;
    }

    private void updateDebitTransactionStatus(String sagaId, TransactionStatus status) {
        transactionRepository.findByReference(sagaId).stream()
                .filter(t -> "DEBIT_MOBILE_MONEY".equals(t.getType()))
                .findFirst()
                .ifPresent(t -> {
                    t.setStatus(status);
                    if (status == TransactionStatus.COMPLETED) {
                        t.setCompletedAt(LocalDateTime.now());
                    }
                    transactionRepository.save(t);
                    log.info("Transaction mise à jour - Reference: {}, Status: {}", sagaId, status);
                });
    }
}
package com.orabank.smsbanking.service;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.util.LoggingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;

    @Value("${internal-transfer.fees.enabled:true}")
    private boolean internalTransferFeesEnabled;

    @Value("${internal-transfer.fees.percentage:0.5}")
    private BigDecimal internalTransferFeesPercentage;

    @Value("${internal-transfer.fees.min-amount:50}")
    private BigDecimal internalTransferFeesMin;

    @Value("${internal-transfer.fees.max-amount:2000}")
    private BigDecimal internalTransferFeesMax;

    @Value("${internal-transfer.fees.account-number:FEE_INTERNAL_TRANSFER_001}")
    private String internalTransferFeesAccountNumber;

    // ============================================================
    // MÉTHODE UTILITAIRE : Récupération du compte avec gestion multi-comptes
    // ============================================================

    /**
     * Récupère le compte d'un client.
     * - Si un numéro de compte est spécifié, vérifie qu'il appartient au client
     * - Si un seul compte existe, le retourne
     * - Si plusieurs comptes existent et aucun n'est spécifié, lève une exception
     *
     * @param client le client
     * @param accountNumber le numéro de compte (peut être null)
     * @return le compte trouvé
     * @throws ClientNotFoundException si le compte n'existe pas ou si plusieurs comptes trouvés
     */
    private Account getClientAccount(Client client, String accountNumber) {
        List<Account> accounts = accountRepository.findAllByClientId(client.getId());

        if (accounts.isEmpty()) {
            throw new ClientNotFoundException("Aucun compte trouvé pour ce client");
        }

        // Si un numéro de compte est spécifié
        if (accountNumber != null && !accountNumber.isEmpty()) {
            return accounts.stream()
                    .filter(a -> a.getAccountNumber().equalsIgnoreCase(accountNumber))
                    .findFirst()
                    .orElseThrow(() -> new ClientNotFoundException(
                            String.format("Compte %s non trouvé pour ce client. Comptes disponibles: %s",
                                    accountNumber,
                                    accounts.stream().map(Account::getAccountNumber).collect(Collectors.joining(", ")))));
        }

        // Si un seul compte existe
        if (accounts.size() == 1) {
            return accounts.get(0);
        }

        // Si plusieurs comptes existent, lever une exception avec la liste des comptes
        String accountList = accounts.stream()
                .map(Account::getAccountNumber)
                .collect(Collectors.joining(", "));
        throw new ClientNotFoundException(
                String.format("Plusieurs comptes trouvés. Veuillez spécifier le compte: %s", accountList));
    }

    /**
     * Version simplifiée pour les méthodes qui n'ont pas besoin de spécifier de compte
     * Utilise le compte par défaut ou le premier compte actif
     */
    private Account getClientDefaultAccount(Client client) {
        List<Account> accounts = accountRepository.findByClientIdAndActiveTrue(client.getId());

        if (accounts.isEmpty()) {
            throw new ClientNotFoundException("Aucun compte actif trouvé pour ce client");
        }

        // Si un seul compte, le retourner
        if (accounts.size() == 1) {
            return accounts.get(0);
        }

        // Sinon, utiliser le premier compte actif (ou le compte avec le plus petit ID)
        return accounts.stream()
                .min((a1, a2) -> a1.getId().compareTo(a2.getId()))
                .orElse(accounts.get(0));
    }

    @PostConstruct
    @Transactional
    public void initializeSystemAccounts() {
        log.info("=== INITIALISATION DES COMPTES SYSTÈME ===");

        try {
            // 1. Compte de frais Mobile Money
            boolean mobileFeesAccountExists = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").isPresent();

            if (!mobileFeesAccountExists) {
                log.info("Création du compte de frais Mobile Money...");
                Account feesAccount = Account.builder()
                        .accountNumber("FEE_MOBILE_MONEY_001")
                        .clientId(null)
                        .balance(BigDecimal.ZERO)
                        .currency("XOF")
                        .active(true)
                        .accountType(Account.AccountType.CURRENT)
                        .status(Account.AccountStatus.ACTIVE)
                        .systemAccount(true)
                        .description("Compte collecteur des frais pour les transferts Mobile Money")
                        .feeType("MOBILE_MONEY_TRANSFER")
                        .build();
                Account saved = accountRepository.save(feesAccount);
                log.info("Compte de frais Mobile Money créé - Numéro: {}", saved.getAccountNumber());
            } else {
                log.info("Compte de frais Mobile Money existe déjà");
            }

            // 2. Compte de frais pour virements internes
            boolean internalFeesAccountExists = accountRepository.findByAccountNumber(internalTransferFeesAccountNumber).isPresent();

            if (!internalFeesAccountExists) {
                log.info("Création du compte de frais pour virements internes...");
                Account internalFeesAccount = Account.builder()
                        .accountNumber(internalTransferFeesAccountNumber)
                        .clientId(null)
                        .balance(BigDecimal.ZERO)
                        .currency("XOF")
                        .active(true)
                        .accountType(Account.AccountType.CURRENT)
                        .status(Account.AccountStatus.ACTIVE)
                        .systemAccount(true)
                        .description("Compte collecteur des frais pour les virements internes")
                        .feeType("INTERNAL_TRANSFER")
                        .build();
                Account saved = accountRepository.save(internalFeesAccount);
                log.info("Compte de frais pour virements internes créé - Numéro: {}", saved.getAccountNumber());
            } else {
                log.info("Compte de frais pour virements internes existe déjà");
            }

            // 3. Mettre à jour la devise des comptes existants
            List<Account> allAccounts = accountRepository.findAll();
            int updatedCount = 0;
            for (Account account : allAccounts) {
                if (account.getCurrency() != null && account.getCurrency().equals("FCFA")) {
                    account.setCurrency("XOF");
                    accountRepository.save(account);
                    updatedCount++;
                }
            }
            if (updatedCount > 0) {
                log.info("{} compte(s) mis à jour de FCFA vers XOF", updatedCount);
            }

            // 4. Mettre à jour le solde du compte de frais Mobile Money si nécessaire
            Account mobileFeesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
            if (mobileFeesAccount != null && mobileFeesAccount.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal totalFees = transactionRepository.findTotalFeesCollected();
                if (totalFees != null && totalFees.compareTo(BigDecimal.ZERO) > 0) {
                    mobileFeesAccount.setBalance(totalFees);
                    accountRepository.save(mobileFeesAccount);
                    log.info("💰 Solde du compte de frais Mobile Money mis à jour: {} FCFA", totalFees);
                }
            }

            log.info("=== INITIALISATION TERMINÉE ===");

        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation des comptes système", e);
        }
    }

    // ============================================================
    // MÉTHODES EXISTANTES CORRIGÉES
    // ============================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public BigDecimal getBalance(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientDefaultAccount(client);

        log.info("Consultation solde - Client: {}, Compte: {}, Solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                account.getBalance());

        return account.getBalance();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public BigDecimal getBalance(String phoneNumber, String accountNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientAccount(client, accountNumber);

        log.info("Consultation solde - Client: {}, Compte: {}, Solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                account.getBalance());

        return account.getBalance();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getLastTransactions(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientDefaultAccount(client);

        List<Transaction> transactions = transactionRepository
                .findTop5ByAccountIdOrderByCreatedAtDesc(account.getId());

        log.info("Récupération historique - Client: {}, Compte: {}, Nombre transactions: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                transactions.size());

        return transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getLastTransactions(String phoneNumber, String accountNumber, int limit) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientAccount(client, accountNumber);

        // Utiliser la méthode avec limit dynamique
        List<Transaction> transactions = transactionRepository
                .findTopByAccountIdOrderByCreatedAtDesc(account.getId(), limit);

        log.info("Récupération historique - Client: {}, Compte: {}, Limite: {}, Nombre transactions: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                limit,
                transactions.size());

        return transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getLastTransactionsByClientId(Long clientId, int limit) {
        List<Transaction> transactions = transactionRepository
                .findTopByClientIdOrderByCreatedAtDesc(clientId, limit);

        log.info("Récupération historique par client - ClientId: {}, Nombre transactions: {}",
                clientId, transactions.size());

        return transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getTransactionsByAccountAndType(String phoneNumber, String accountNumber,
                                                             String type, int limit) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientAccount(client, accountNumber);

        List<Transaction> transactions = transactionRepository
                .findTopByAccountIdAndTypeOrderByCreatedAtDesc(account.getId(), type, limit);

        log.info("Récupération transactions par type - Client: {}, Compte: {}, Type: {}, Nombre: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                type,
                transactions.size());

        return transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getTransactionsByDateRange(String phoneNumber, String accountNumber,
                                                        LocalDateTime startDate,
                                                        LocalDateTime endDate,
                                                        int limit) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientAccount(client, accountNumber);

        List<Transaction> transactions = transactionRepository
                .findTransactionsByAccountIdAndDateRange(account.getId(), startDate, endDate, limit);

        log.info("Récupération transactions par date - Client: {}, Compte: {}, Période: {} - {}, Nombre: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                startDate,
                endDate,
                transactions.size());

        return transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getTransactionsWithPagination(String phoneNumber, String accountNumber,
                                                           int limit, int offset) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientAccount(client, accountNumber);

        List<Transaction> transactions = transactionRepository
                .findTransactionsByAccountIdWithPagination(account.getId(), limit, offset);

        log.info("Récupération transactions paginées - Client: {}, Compte: {}, Limit: {}, Offset: {}, Nombre: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                limit,
                offset,
                transactions.size());

        return transactions;
    }

    @Transactional(readOnly = true)
    public long getTransactionCount(String phoneNumber, String accountNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientAccount(client, accountNumber);

        return transactionRepository.countByAccountId(account.getId());
    }

    @Transactional
    public Transaction debit(String phoneNumber, BigDecimal amount, String description) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientDefaultAccount(client);

        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Tentative de débit échouée - Client: {}, Compte: {}, Montant requis: {} FCFA, Solde actuel: {} FCFA",
                    LoggingUtil.maskPhoneNumber(phoneNumber),
                    account.getAccountNumber(),
                    amount,
                    account.getBalance());
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant sur le compte %s. Solde actuel: %d FCFA, Montant demandé: %d FCFA",
                            account.getAccountNumber(),
                            account.getBalance().longValue(),
                            amount.longValue()));
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(amount);
        transaction.setType("DEBIT");
        transaction.setDescription(description + " - Compte: " + account.getAccountNumber());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Débit effectué - Client: {}, Compte: {}, Montant: {} FCFA, Nouveau solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                amount,
                account.getBalance());

        return savedTransaction;
    }

    @Transactional
    public Transaction credit(String phoneNumber, BigDecimal amount, String description) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientDefaultAccount(client);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(amount);
        transaction.setType("CREDIT");
        transaction.setDescription(description + " - Compte: " + account.getAccountNumber());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Crédit effectué - Client: {}, Compte: {}, Montant: {} FCFA, Nouveau solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                amount,
                account.getBalance());

        return savedTransaction;
    }

    @Transactional
    public Transaction transfer(String senderPhoneNumber, String recipientPhoneNumber,
                                BigDecimal amount, String description) {

        BigDecimal fees = calculateInternalTransferFees(amount);
        BigDecimal totalAmount = amount.add(fees);
        String transactionReference = UUID.randomUUID().toString();

        log.info("Virement interne avec frais - Émetteur: {}, Bénéficiaire: {}, Montant: {} FCFA, Frais: {} FCFA, Total: {} FCFA, Réf: {}",
                LoggingUtil.maskPhoneNumber(senderPhoneNumber),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
                amount, fees, totalAmount, transactionReference);

        Client senderClient = clientRepository.findByPhoneNumber(senderPhoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client émetteur non trouvé: " + LoggingUtil.maskPhoneNumber(senderPhoneNumber)));

        Account senderAccount = getClientDefaultAccount(senderClient);

        Client recipientClient = clientRepository.findByPhoneNumber(recipientPhoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client bénéficiaire non trouvé: " + LoggingUtil.maskPhoneNumber(recipientPhoneNumber)));

        Account recipientAccount = getClientDefaultAccount(recipientClient);

        if (senderAccount.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Solde insuffisant pour virement interne - Compte: {}, Solde: {} FCFA, Montant requis: {} FCFA (dont frais: {} FCFA)",
                    senderAccount.getAccountNumber(),
                    senderAccount.getBalance(),
                    totalAmount,
                    fees);
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant sur le compte %s. Solde actuel: %d FCFA, Montant requis (avec frais): %d FCFA",
                            senderAccount.getAccountNumber(),
                            senderAccount.getBalance().longValue(),
                            totalAmount.longValue()));
        }

        senderAccount.setBalance(senderAccount.getBalance().subtract(totalAmount));
        accountRepository.save(senderAccount);

        Transaction debitTransaction = new Transaction();
        debitTransaction.setAccountId(senderAccount.getId());
        debitTransaction.setAmount(totalAmount);
        debitTransaction.setType("VIREMENT_INTERNE");
        debitTransaction.setReference(transactionReference);
        debitTransaction.setDescription(description + " - Virement vers " +
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber) +
                " (" + recipientAccount.getAccountNumber() + ") depuis " +
                senderAccount.getAccountNumber());
        debitTransaction.setStatus(TransactionStatus.COMPLETED);
        debitTransaction.setCreatedAt(LocalDateTime.now());
        Transaction savedDebitTransaction = transactionRepository.save(debitTransaction);

        log.info("Débit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                senderAccount.getAccountNumber(),
                totalAmount,
                transactionReference,
                senderAccount.getBalance());

        recipientAccount.setBalance(recipientAccount.getBalance().add(amount));
        accountRepository.save(recipientAccount);

        Transaction creditTransaction = new Transaction();
        creditTransaction.setAccountId(recipientAccount.getId());
        creditTransaction.setAmount(amount);
        creditTransaction.setType("VIREMENT_INTERNE");
        creditTransaction.setReference(transactionReference);
        creditTransaction.setDescription(description + " - Reçu de " +
                LoggingUtil.maskPhoneNumber(senderPhoneNumber) +
                " (" + senderAccount.getAccountNumber() + ")");
        creditTransaction.setStatus(TransactionStatus.COMPLETED);
        creditTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(creditTransaction);

        log.info("Crédit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                recipientAccount.getAccountNumber(),
                amount,
                transactionReference,
                recipientAccount.getBalance());

        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            creditInternalTransferFeesAccount(fees, transactionReference, amount);
        }

        log.info("Virement interne effectué - Émetteur: {}, Compte source: {}, Bénéficiaire: {}, Compte dest: {}, Montant: {} FCFA, Frais: {} FCFA, Réf: {}",
                LoggingUtil.maskPhoneNumber(senderPhoneNumber),
                senderAccount.getAccountNumber(),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
                recipientAccount.getAccountNumber(),
                amount, fees, transactionReference);

        return savedDebitTransaction;
    }

    private BigDecimal calculateInternalTransferFees(BigDecimal amount) {
        if (!internalTransferFeesEnabled) {
            return BigDecimal.ZERO;
        }

        BigDecimal percentageDivisor = new BigDecimal("100");
        BigDecimal calculatedFees = amount
                .multiply(internalTransferFeesPercentage)
                .divide(percentageDivisor, 0, RoundingMode.HALF_UP);

        return calculatedFees.max(internalTransferFeesMin).min(internalTransferFeesMax);
    }

    @Transactional
    public Transaction creditInternalTransferFeesAccount(BigDecimal feesAmount, String reference, BigDecimal transferAmount) {
        Account feesAccount = accountRepository.findByAccountNumber(internalTransferFeesAccountNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Compte de frais pour virements internes non trouvé: " + internalTransferFeesAccountNumber));

        feesAccount.setBalance(feesAccount.getBalance().add(feesAmount));
        accountRepository.save(feesAccount);

        Transaction transaction = new Transaction();
        transaction.setAccountId(feesAccount.getId());
        transaction.setAmount(feesAmount);
        transaction.setType("CREDIT_FEES");
        transaction.setReference(reference);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(String.format("Frais virement interne - Transfert: %d FCFA, Taux: %.1f%%",
                transferAmount.longValue(), internalTransferFeesPercentage));
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Compte de frais virement interne crédité - AccountId: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                feesAccount.getId(), feesAmount, reference, feesAccount.getBalance());

        return savedTransaction;
    }

    @Transactional
    public Transaction debitWithFees(String phoneNumber, BigDecimal transferAmount,
                                     BigDecimal fees, String description, String sagaId) {
        BigDecimal totalAmount = transferAmount.add(fees);

        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientDefaultAccount(client);

        if (account.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Tentative de débit échouée (avec frais) - Client: {}, Compte: {}, Montant: {} FCFA, Frais: {} FCFA, Total: {} FCFA, Solde: {} FCFA",
                    LoggingUtil.maskPhoneNumber(phoneNumber),
                    account.getAccountNumber(),
                    transferAmount,
                    fees,
                    totalAmount,
                    account.getBalance());
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant sur le compte %s. Solde actuel: %d FCFA, Montant requis (avec frais): %d FCFA",
                            account.getAccountNumber(),
                            account.getBalance().longValue(),
                            totalAmount.longValue()));
        }

        account.setBalance(account.getBalance().subtract(totalAmount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(totalAmount);
        transaction.setType("DEBIT_MOBILE_MONEY");
        transaction.setReference(sagaId);
        transaction.setDescription(String.format("Transfert Mobile Money - %d FCFA (Frais inclus) depuis %s",
                totalAmount.longValue(), account.getAccountNumber()));
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Débit avec frais effectué - Client: {}, Compte: {}, Transfert: {} FCFA, Frais: {} FCFA, Total: {} FCFA, Saga: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                transferAmount,
                fees,
                totalAmount,
                sagaId);

        return savedTransaction;
    }

    @Transactional
    public Transaction creditFeesAccount(BigDecimal feesAmount, String description, String sagaId) {
        Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001")
                .orElseThrow(() -> new IllegalStateException("Compte de frais Mobile Money (FEE_MOBILE_MONEY_001) non configuré"));

        feesAccount.setBalance(feesAccount.getBalance().add(feesAmount));
        accountRepository.save(feesAccount);

        Transaction transaction = new Transaction();
        transaction.setAccountId(feesAccount.getId());
        transaction.setAmount(feesAmount);
        transaction.setType("CREDIT_FEES");
        transaction.setReference(sagaId);
        transaction.setDescription(String.format("Frais Mobile Money - %s (Montant: %d FCFA)",
                description, feesAmount.longValue()));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Compte de frais crédité - AccountId: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                feesAccount.getId(), feesAmount, sagaId, feesAccount.getBalance());

        return savedTransaction;
    }

    @Transactional
    public Transaction compensateClient(String phoneNumber, BigDecimal transferAmount,
                                        BigDecimal fees, String sagaId) {
        BigDecimal totalAmount = transferAmount.add(fees);

        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = getClientDefaultAccount(client);

        account.setBalance(account.getBalance().add(totalAmount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(totalAmount);
        transaction.setType("CREDIT_COMPENSATION");
        transaction.setReference(sagaId);
        transaction.setDescription(String.format("Compensation - Remboursement suite échec (Transfert: %d FCFA, Frais: %d FCFA) sur %s",
                transferAmount.longValue(), fees.longValue(), account.getAccountNumber()));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Compensation effectuée - Client: {}, Compte: {}, Montant total remboursé: {} FCFA, Saga: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber),
                account.getAccountNumber(),
                totalAmount,
                sagaId);

        return savedTransaction;
    }

    @Transactional(readOnly = true)
    public Optional<Account> getMobileMoneyFeesAccount() {
        return accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001");
    }

    @Transactional(readOnly = true)
    public boolean hasSufficientBalanceWithFees(String phoneNumber, BigDecimal transferAmount, BigDecimal fees) {
        try {
            BigDecimal currentBalance = getBalance(phoneNumber);
            BigDecimal totalRequired = transferAmount.add(fees);
            return currentBalance.compareTo(totalRequired) >= 0;
        } catch (ClientNotFoundException e) {
            log.error("Client non trouvé lors de la vérification de solde: {}", phoneNumber);
            return false;
        }
    }

    @Transactional
    public Account createSystemAccount(String accountNumber, Account.AccountType accountType,
                                       String description, String feeType) {
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .clientId(null)
                .balance(BigDecimal.ZERO)
                .currency("XOF")
                .active(true)
                .accountType(accountType)
                .status(Account.AccountStatus.ACTIVE)
                .systemAccount(true)
                .description(description)
                .feeType(feeType)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Compte système créé - ID: {}, Number: {}, Type: {}",
                saved.getId(), saved.getAccountNumber(), saved.getAccountType());

        return saved;
    }

    // ============================================================
    // NOUVELLES MÉTHODES POUR LA MULTI-COMPTES
    // ============================================================

    @Transactional(readOnly = true)
    public List<Account> getAccountsByPhone(String phoneNumber) {
        log.info("=== getAccountsByPhone START ===");
        log.info("Looking for client with phone: {}", phoneNumber);

        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> {
                    log.error("Client not found for phone: {}", phoneNumber);
                    return new ClientNotFoundException(
                            "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber));
                });

        log.info("Client found: id={}, name={}", client.getId(), client.getFirstName() + " " + client.getLastName());

        List<Account> accounts = accountRepository.findAllByClientId(client.getId());
        log.info("Accounts found: {}", accounts.size());

        for (Account acc : accounts) {
            log.info("Account: id={}, number={}, balance={}, active={}",
                    acc.getId(), acc.getAccountNumber(), acc.getBalance(), acc.isActive());
        }

        return accounts;
    }

    @Transactional(readOnly = true)
    public List<Account> getActiveAccountsByPhone(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        List<Account> accounts = accountRepository.findByClientIdAndActiveTrue(client.getId());
        log.debug("Récupération des comptes actifs - Client: {}, Nombre: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber), accounts.size());

        return accounts;
    }

    @Transactional(readOnly = true)
    public Optional<Account> getAccountByPhoneAndAccountNumber(String phoneNumber, String accountNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        List<Account> accounts = accountRepository.findAllByClientId(client.getId());
        return accounts.stream()
                .filter(a -> a.getAccountNumber().equalsIgnoreCase(accountNumber))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public boolean hasMultipleAccounts(String phoneNumber) {
        List<Account> accounts = getActiveAccountsByPhone(phoneNumber);
        return accounts.size() > 1;
    }

    @Transactional(readOnly = true)
    public int getAccountCount(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        return accountRepository.findAllByClientId(client.getId()).size();
    }

    @Transactional
    public Transaction transferFromAccount(Account sourceAccount, String recipientPhoneNumber,
                                           BigDecimal amount, String description) {

        BigDecimal fees = calculateInternalTransferFees(amount);
        BigDecimal totalAmount = amount.add(fees);
        String transactionReference = UUID.randomUUID().toString();

        log.info("Virement depuis compte spécifique - Compte source: {}, Bénéficiaire: {}, Montant: {} FCFA, Frais: {} FCFA",
                sourceAccount.getAccountNumber(),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
                amount, fees);

        if (sourceAccount.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Solde insuffisant sur compte {} - Solde: {} FCFA, Montant requis: {} FCFA",
                    sourceAccount.getAccountNumber(), sourceAccount.getBalance(), totalAmount);
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant sur le compte %s. Solde: %d FCFA, Montant requis: %d FCFA",
                            sourceAccount.getAccountNumber(),
                            sourceAccount.getBalance().longValue(),
                            totalAmount.longValue()));
        }

        Client recipientClient = clientRepository.findByPhoneNumber(recipientPhoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client bénéficiaire non trouvé: " + LoggingUtil.maskPhoneNumber(recipientPhoneNumber)));

        Account recipientAccount = getClientDefaultAccount(recipientClient);

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmount));
        accountRepository.save(sourceAccount);

        Transaction debitTransaction = new Transaction();
        debitTransaction.setAccountId(sourceAccount.getId());
        debitTransaction.setAmount(totalAmount);
        debitTransaction.setType("VIREMENT_INTERNE");
        debitTransaction.setReference(transactionReference);
        debitTransaction.setDescription(description + " - Virement vers " +
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber) +
                " (" + recipientAccount.getAccountNumber() + ") depuis " +
                sourceAccount.getAccountNumber());
        debitTransaction.setStatus(TransactionStatus.COMPLETED);
        debitTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(debitTransaction);

        log.info("Débit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                sourceAccount.getAccountNumber(), totalAmount, transactionReference, sourceAccount.getBalance());

        recipientAccount.setBalance(recipientAccount.getBalance().add(amount));
        accountRepository.save(recipientAccount);

        Transaction creditTransaction = new Transaction();
        creditTransaction.setAccountId(recipientAccount.getId());
        creditTransaction.setAmount(amount);
        creditTransaction.setType("VIREMENT_INTERNE");
        creditTransaction.setReference(transactionReference);
        creditTransaction.setDescription(description + " - Reçu de " +
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber) +
                " (" + sourceAccount.getAccountNumber() + ")");
        creditTransaction.setStatus(TransactionStatus.COMPLETED);
        creditTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(creditTransaction);

        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            creditInternalTransferFeesAccount(fees, transactionReference, amount);
        }

        log.info("Virement interne effectué - Compte source: {}, Bénéficiaire: {}, Montant: {} FCFA, Frais: {} FCFA",
                sourceAccount.getAccountNumber(),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
                amount, fees);

        return debitTransaction;
    }

    // ============================================================
    // MÉTHODE : TRANSFERT AVEC VÉRIFICATION DU COMPTE DESTINATAIRE
    // ============================================================

    /**
     * Effectue un transfert depuis un compte source vers un compte destinataire spécifique
     *
     * @param sourceAccount le compte source (déjà vérifié)
     * @param recipientPhoneNumber le numéro de téléphone du bénéficiaire
     * @param recipientAccountNumber le numéro de compte du bénéficiaire (optionnel)
     * @param amount le montant à transférer
     * @param description la description du transfert
     * @return la transaction effectuée
     */
    @Transactional
    public Transaction transferFromAccountWithTargetAccount(Account sourceAccount,
                                                            String recipientPhoneNumber,
                                                            String recipientAccountNumber,
                                                            BigDecimal amount,
                                                            String description) {

        BigDecimal fees = calculateInternalTransferFees(amount);
        BigDecimal totalAmount = amount.add(fees);
        String transactionReference = UUID.randomUUID().toString();

        log.info("Virement depuis compte spécifique - Compte source: {}, Bénéficiaire: {}, Compte destinataire: {}, Montant: {} FCFA, Frais: {} FCFA",
                sourceAccount.getAccountNumber(),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
                recipientAccountNumber != null ? recipientAccountNumber : "AUTO",
                amount, fees);

        // 🔒 VÉRIFICATION 1 : Vérifier le solde du compte source
        if (sourceAccount.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Solde insuffisant sur compte {} - Solde: {} FCFA, Montant requis: {} FCFA",
                    sourceAccount.getAccountNumber(), sourceAccount.getBalance(), totalAmount);
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant sur le compte %s. Solde: %d FCFA, Montant requis: %d FCFA",
                            sourceAccount.getAccountNumber(),
                            sourceAccount.getBalance().longValue(),
                            totalAmount.longValue()));
        }

        // 🔒 VÉRIFICATION 2 : Vérifier que le bénéficiaire existe
        Client recipientClient = clientRepository.findByPhoneNumber(recipientPhoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client bénéficiaire non trouvé: " + LoggingUtil.maskPhoneNumber(recipientPhoneNumber)));

        // 🔒 VÉRIFICATION 3 : Récupérer les comptes du bénéficiaire
        List<Account> recipientAccounts = accountRepository.findAllByClientId(recipientClient.getId());
        if (recipientAccounts.isEmpty()) {
            throw new ClientNotFoundException("Aucun compte trouvé pour le bénéficiaire");
        }

        // 🔒 VÉRIFICATION 4 : Sélectionner le compte destinataire
        Account recipientAccount;
        if (recipientAccountNumber != null && !recipientAccountNumber.isEmpty()) {
            // 🔒 VÉRIFICATION : Le compte spécifié appartient-il au bénéficiaire ?
            recipientAccount = recipientAccounts.stream()
                    .filter(a -> a.getAccountNumber().equalsIgnoreCase(recipientAccountNumber))
                    .findFirst()
                    .orElseThrow(() -> new ClientNotFoundException(
                            String.format("Le compte %s n'appartient pas au bénéficiaire %s",
                                    recipientAccountNumber,
                                    LoggingUtil.maskPhoneNumber(recipientPhoneNumber))));
            log.info("Compte destinataire spécifié trouvé: {}", recipientAccount.getAccountNumber());
        } else {
            // 🔒 VÉRIFICATION : Si aucun compte spécifié, utiliser le premier compte actif
            if (recipientAccounts.size() == 1) {
                recipientAccount = recipientAccounts.get(0);
                log.info("Un seul compte destinataire trouvé: {}", recipientAccount.getAccountNumber());
            } else {
                // 🔒 SÉCURITÉ : Plusieurs comptes mais aucun spécifié → Demander au client
                String accountList = recipientAccounts.stream()
                        .map(Account::getAccountNumber)
                        .collect(Collectors.joining(", "));
                throw new ClientNotFoundException(
                        String.format("Bénéficiaire a plusieurs comptes. Veuillez spécifier le compte: %s",
                                accountList));
            }
        }

        // 🔒 VÉRIFICATION 5 : Empêcher le transfert vers soi-même
        if (sourceAccount.getClientId().equals(recipientAccount.getClientId())) {
            throw new IllegalArgumentException("Impossible de transférer vers votre propre compte");
        }

        // 1. 🔒 DEBIT : Débiter l'émetteur
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmount));
        accountRepository.save(sourceAccount);

        Transaction debitTransaction = new Transaction();
        debitTransaction.setAccountId(sourceAccount.getId());
        debitTransaction.setAmount(totalAmount);
        debitTransaction.setType("VIREMENT_INTERNE");
        debitTransaction.setReference(transactionReference);
        debitTransaction.setDescription(description + " - Virement vers " +
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber) +
                " (" + recipientAccount.getAccountNumber() + ") depuis " +
                sourceAccount.getAccountNumber());
        debitTransaction.setStatus(TransactionStatus.COMPLETED);
        debitTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(debitTransaction);

        log.info("Débit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                sourceAccount.getAccountNumber(), totalAmount, transactionReference, sourceAccount.getBalance());

        // 2. 🔒 CREDIT : Créditer le bénéficiaire
        recipientAccount.setBalance(recipientAccount.getBalance().add(amount));
        accountRepository.save(recipientAccount);

        Transaction creditTransaction = new Transaction();
        creditTransaction.setAccountId(recipientAccount.getId());
        creditTransaction.setAmount(amount);
        creditTransaction.setType("VIREMENT_INTERNE");
        creditTransaction.setReference(transactionReference);
        creditTransaction.setDescription(description + " - Reçu de " +
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber) +
                " (" + sourceAccount.getAccountNumber() + ")");
        creditTransaction.setStatus(TransactionStatus.COMPLETED);
        creditTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(creditTransaction);

        log.info("Crédit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                recipientAccount.getAccountNumber(), amount, transactionReference, recipientAccount.getBalance());

        // 3. 🔒 FRAIS : Créditer le compte de frais si nécessaire
        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            creditInternalTransferFeesAccount(fees, transactionReference, amount);
        }

        log.info("Virement interne effectué - Compte source: {}, Compte destinataire: {}, Montant: {} FCFA, Frais: {} FCFA, Réf: {}",
                sourceAccount.getAccountNumber(),
                recipientAccount.getAccountNumber(),
                amount, fees, transactionReference);

        return debitTransaction;
    }
}
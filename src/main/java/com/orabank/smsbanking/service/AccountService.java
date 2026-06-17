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
    // MÉTHODES EXISTANTES (non modifiées)
    // ============================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public BigDecimal getBalance(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = accountRepository.findByClientId(client.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour ce client"));

        log.info("Consultation solde - Client: {}, Solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber), account.getBalance());

        return account.getBalance();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Transaction> getLastTransactions(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = accountRepository.findByClientId(client.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour ce client"));

        List<Transaction> transactions = transactionRepository
                .findTop5ByAccountIdOrderByCreatedAtDesc(account.getId());

        log.info("Récupération historique - Client: {}, Nombre transactions: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber), transactions.size());

        return transactions;
    }

    @Transactional
    public Transaction debit(String phoneNumber, BigDecimal amount, String description) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = accountRepository.findByClientId(client.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour ce client"));

        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Tentative de débit échouée - Client: {}, Montant requis: {} FCFA, Solde actuel: {} FCFA",
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount, account.getBalance());
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant. Solde actuel: %d FCFA, Montant demandé: %d FCFA",
                            account.getBalance().longValue(), amount.longValue()));
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(amount);
        transaction.setType("DEBIT");
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Débit effectué - Client: {}, Montant: {} FCFA, Nouveau solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber), amount, account.getBalance());

        return savedTransaction;
    }

    @Transactional
    public Transaction credit(String phoneNumber, BigDecimal amount, String description) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        Account account = accountRepository.findByClientId(client.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour ce client"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(amount);
        transaction.setType("CREDIT");
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Crédit effectué - Client: {}, Montant: {} FCFA, Nouveau solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber), amount, account.getBalance());

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

        Account senderAccount = accountRepository.findByClientId(senderClient.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour l'émetteur"));

        Client recipientClient = clientRepository.findByPhoneNumber(recipientPhoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client bénéficiaire non trouvé: " + LoggingUtil.maskPhoneNumber(recipientPhoneNumber)));

        Account recipientAccount = accountRepository.findByClientId(recipientClient.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour le bénéficiaire"));

        if (senderAccount.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Solde insuffisant pour virement interne - Solde: {} FCFA, Montant requis: {} FCFA (dont frais: {} FCFA)",
                    senderAccount.getBalance(), totalAmount, fees);
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant. Solde actuel: %d FCFA, Montant requis (avec frais): %d FCFA",
                            senderAccount.getBalance().longValue(), totalAmount.longValue()));
        }

        // 1. Débiter l'émetteur du montant total
        senderAccount.setBalance(senderAccount.getBalance().subtract(totalAmount));
        accountRepository.save(senderAccount);

        Transaction debitTransaction = new Transaction();
        debitTransaction.setAccountId(senderAccount.getId());
        debitTransaction.setAmount(totalAmount);
        debitTransaction.setType("VIREMENT_INTERNE");
        debitTransaction.setReference(transactionReference);
        debitTransaction.setDescription(description + " - Virement vers " + LoggingUtil.maskPhoneNumber(recipientPhoneNumber));
        debitTransaction.setStatus(TransactionStatus.COMPLETED);
        debitTransaction.setCreatedAt(LocalDateTime.now());
        Transaction savedDebitTransaction = transactionRepository.save(debitTransaction);

        log.info("Débit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                senderAccount.getAccountNumber(), totalAmount, transactionReference, senderAccount.getBalance());

        // 2. Créditer le bénéficiaire du montant du transfert uniquement
        recipientAccount.setBalance(recipientAccount.getBalance().add(amount));
        accountRepository.save(recipientAccount);

        Transaction creditTransaction = new Transaction();
        creditTransaction.setAccountId(recipientAccount.getId());
        creditTransaction.setAmount(amount);
        creditTransaction.setType("VIREMENT_INTERNE");
        creditTransaction.setReference(transactionReference);
        creditTransaction.setDescription(description + " - Reçu de " + LoggingUtil.maskPhoneNumber(senderPhoneNumber));
        creditTransaction.setStatus(TransactionStatus.COMPLETED);
        creditTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(creditTransaction);

        log.info("Crédit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                recipientAccount.getAccountNumber(), amount, transactionReference, recipientAccount.getBalance());

        // 3. Créditer le compte de frais si des frais ont été appliqués
        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            creditInternalTransferFeesAccount(fees, transactionReference, amount);
        }

        log.info("Virement interne effectué - Émetteur: {}, Bénéficiaire: {}, Montant: {} FCFA, Frais: {} FCFA, Réf: {}",
                LoggingUtil.maskPhoneNumber(senderPhoneNumber),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
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

        Account account = accountRepository.findByClientId(client.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour ce client"));

        if (account.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Tentative de débit échouée (avec frais) - Client: {}, Montant: {} FCFA, Frais: {} FCFA, Total: {} FCFA, Solde: {} FCFA",
                    LoggingUtil.maskPhoneNumber(phoneNumber), transferAmount, fees, totalAmount, account.getBalance());
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant. Solde actuel: %d FCFA, Montant requis (avec frais): %d FCFA",
                            account.getBalance().longValue(), totalAmount.longValue()));
        }

        account.setBalance(account.getBalance().subtract(totalAmount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(totalAmount);
        transaction.setType("DEBIT_MOBILE_MONEY");
        transaction.setReference(sagaId);
        transaction.setDescription(String.format("Transfert Mobile Money - %d FCFA (Frais inclus)",
                totalAmount.longValue()));
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Débit avec frais effectué - Client: {}, Transfert: {} FCFA, Frais: {} FCFA, Total: {} FCFA, Saga: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber), transferAmount, fees, totalAmount, sagaId);

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

        Account account = accountRepository.findByClientId(client.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour ce client"));

        account.setBalance(account.getBalance().add(totalAmount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAmount(totalAmount);
        transaction.setType("CREDIT_COMPENSATION");
        transaction.setReference(sagaId);
        transaction.setDescription(String.format("Compensation - Remboursement suite échec (Transfert: %d FCFA, Frais: %d FCFA)",
                transferAmount.longValue(), fees.longValue()));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Compensation effectuée - Client: {}, Montant total remboursé: {} FCFA, Saga: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber), totalAmount, sagaId);

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

    /**
     * Récupère la liste des comptes d'un client par son numéro de téléphone
     */
    @Transactional(readOnly = true)
    public List<Account> getAccountsByPhone(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        List<Account> accounts = accountRepository.findAllByClientId(client.getId());
        log.debug("Récupération des comptes - Client: {}, Nombre de comptes: {}",
                LoggingUtil.maskPhoneNumber(phoneNumber), accounts.size());

        return accounts;
    }

    /**
     * Récupère les comptes actifs d'un client par son numéro de téléphone
     */
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

    /**
     * Récupère un compte spécifique par clientId et numéro de compte
     */
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

    /**
     * Récupère le solde d'un compte spécifique
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String phoneNumber, String accountNumber) {
        Account account = getAccountByPhoneAndAccountNumber(phoneNumber, accountNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Compte " + accountNumber + " non trouvé pour le client: " +
                                LoggingUtil.maskPhoneNumber(phoneNumber)));

        log.info("Consultation solde - Client: {}, Compte: {}, Solde: {} FCFA",
                LoggingUtil.maskPhoneNumber(phoneNumber), accountNumber, account.getBalance());

        return account.getBalance();
    }

    /**
     * Vérifie si un client a plusieurs comptes actifs
     */
    @Transactional(readOnly = true)
    public boolean hasMultipleAccounts(String phoneNumber) {
        List<Account> accounts = getActiveAccountsByPhone(phoneNumber);
        return accounts.size() > 1;
    }

    /**
     * Récupère le nombre de comptes d'un client
     */
    @Transactional(readOnly = true)
    public int getAccountCount(String phoneNumber) {
        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client non trouvé pour le numéro: " + LoggingUtil.maskPhoneNumber(phoneNumber)));

        return accountRepository.findAllByClientId(client.getId()).size();
    }

    /**
     * Effectue un transfert depuis un compte spécifique
     */
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

        // Vérifier le solde du compte source
        if (sourceAccount.getBalance().compareTo(totalAmount) < 0) {
            log.warn("Solde insuffisant sur compte {} - Solde: {} FCFA, Montant requis: {} FCFA",
                    sourceAccount.getAccountNumber(), sourceAccount.getBalance(), totalAmount);
            throw new InsufficientBalanceException(
                    String.format("Solde insuffisant sur le compte %s. Solde: %d FCFA, Montant requis: %d FCFA",
                            sourceAccount.getAccountNumber(),
                            sourceAccount.getBalance().longValue(),
                            totalAmount.longValue()));
        }

        // Récupérer le bénéficiaire
        Client recipientClient = clientRepository.findByPhoneNumber(recipientPhoneNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Client bénéficiaire non trouvé: " + LoggingUtil.maskPhoneNumber(recipientPhoneNumber)));

        Account recipientAccount = accountRepository.findByClientId(recipientClient.getId())
                .orElseThrow(() -> new ClientNotFoundException("Aucun compte trouvé pour le bénéficiaire"));

        // 1. Débiter l'émetteur
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmount));
        accountRepository.save(sourceAccount);

        Transaction debitTransaction = new Transaction();
        debitTransaction.setAccountId(sourceAccount.getId());
        debitTransaction.setAmount(totalAmount);
        debitTransaction.setType("VIREMENT_INTERNE");
        debitTransaction.setReference(transactionReference);
        debitTransaction.setDescription(description + " - Virement vers " +
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber) +
                " depuis " + sourceAccount.getAccountNumber());
        debitTransaction.setStatus(TransactionStatus.COMPLETED);
        debitTransaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(debitTransaction);

        log.info("Débit virement interne - Compte: {}, Montant: {} FCFA, Réf: {}, Nouveau solde: {} FCFA",
                sourceAccount.getAccountNumber(), totalAmount, transactionReference, sourceAccount.getBalance());

        // 2. Créditer le bénéficiaire
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

        // 3. Créditer le compte de frais si nécessaire
        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            creditInternalTransferFeesAccount(fees, transactionReference, amount);
        }

        log.info("Virement interne effectué - Compte source: {}, Bénéficiaire: {}, Montant: {} FCFA, Frais: {} FCFA",
                sourceAccount.getAccountNumber(),
                LoggingUtil.maskPhoneNumber(recipientPhoneNumber),
                amount, fees);

        return debitTransaction;
    }

    /**
     * Récupère l'historique des transactions d'un compte spécifique
     */
    @Transactional(readOnly = true)
    public List<Transaction> getLastTransactions(String phoneNumber, String accountNumber, int limit) {
        Account account = getAccountByPhoneAndAccountNumber(phoneNumber, accountNumber)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Compte " + accountNumber + " non trouvé pour le client: " +
                                LoggingUtil.maskPhoneNumber(phoneNumber)));

        return transactionRepository.findTop5ByAccountIdOrderByCreatedAtDesc(account.getId());
    }
}
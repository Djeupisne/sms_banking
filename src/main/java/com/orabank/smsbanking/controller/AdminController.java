package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.dto.request.TransferRequestDto;
import com.orabank.smsbanking.dto.request.InternalTransferRequest;
import com.orabank.smsbanking.dto.request.AccountOperationRequest;
import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.service.AccountService;
import com.orabank.smsbanking.service.MobileMoneyService;
import com.orabank.smsbanking.service.TransactionLoggingService;
import com.orabank.smsbanking.util.AppConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MobileMoneyService mobileMoneyService;
    private final AccountService accountService;
    private final TransactionLoggingService loggingService;
    private final HttpServletRequest httpServletRequest;

    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("1");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[A-Za-z0-9]{8,20}$");
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");

    @GetMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Client>> getAllClients() {
        log.info("Récupération de la liste des clients");
        return ResponseEntity.ok(clientRepository.findAll());
    }

    @PostMapping("/transfers/mobile-money")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> transferToMobileMoney(@Valid @RequestBody TransferRequestDto transferRequest) {
        log.info("Demande de virement Mobile Money - Compte source: {}, Montant: {}, Destinataire: {}",
                maskAccountNumber(transferRequest.getAccountNumber()),
                transferRequest.getAmount(),
                maskPhoneNumber(transferRequest.getRecipientPhone()));

        Double amount = transferRequest.getAmount().doubleValue();
        Double fees = Math.ceil(amount * 0.1);
        Double totalAmount = amount + fees;
        String transactionRef = UUID.randomUUID().toString();

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(transferRequest.getAccountNumber()).matches()) {
                loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                        transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                        null, transactionRef, "FAILED", "Numéro de compte invalide", fees, totalAmount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte invalide\"}");
            }

            Account sourceAccount = accountRepository.findByAccountNumber(transferRequest.getAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte source non trouvé"));

            Client sourceClient = clientRepository.findById(sourceAccount.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client source non trouvé"));

            String senderPhone = sourceClient.getPhoneNumber();

            if (transferRequest.getRecipientPhone() != null &&
                    !PHONE_NUMBER_PATTERN.matcher(transferRequest.getRecipientPhone()).matches()) {
                loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                        transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                        null, transactionRef, "FAILED", "Numéro de téléphone invalide", fees, totalAmount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de téléphone invalide\"}");
            }

            BigDecimal amountBd = transferRequest.getAmount();
            if (amountBd.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                        transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                        null, transactionRef, "FAILED", "Montant inférieur au minimum", fees, totalAmount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body(String.format("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}"));
            }

            if (amountBd.compareTo(new BigDecimal(AppConstants.MAX_TRANSFER_AMOUNT)) > 0) {
                loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                        transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                        null, transactionRef, "FAILED", "Montant supérieur au maximum", fees, totalAmount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body(String.format("{\"status\": \"error\", \"message\": \"Montant maximum: %d FCFA\"}",
                                AppConstants.MAX_TRANSFER_AMOUNT));
            }

            boolean success = mobileMoneyService.transferToMobileMoney(senderPhone, amountBd);

            if (!success) {
                loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                        transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                        null, transactionRef, "FAILED", "Solde insuffisant", fees, totalAmount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Solde insuffisant\"}");
            }

            loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                    transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                    null, transactionRef, "SUCCESS", null, fees, totalAmount, httpServletRequest);

            log.info("Virement Mobile Money effectué avec succès");
            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Virement effectué\"}");

        } catch (IllegalArgumentException e) {
            loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                    transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                    null, transactionRef, "FAILED", e.getMessage(), fees, totalAmount, httpServletRequest);
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            loggingService.logTransaction("ADMIN_TRANSFER", "MOBILE_MONEY", amount,
                    transferRequest.getAccountNumber(), null, null, transferRequest.getRecipientPhone(),
                    null, transactionRef, "FAILED", e.getMessage(), fees, totalAmount, httpServletRequest);
            log.error("Erreur lors du virement Mobile Money", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    @PostMapping("/transfers/internal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> internalTransfer(@Valid @RequestBody InternalTransferRequest request) {
        log.info("Demande de virement interne - Source: {}, Cible: {}, Montant: {}",
                maskAccountNumber(request.getSourceAccountNumber()),
                maskAccountNumber(request.getTargetAccountNumber()),
                request.getAmount());

        Double amount = request.getAmount().doubleValue();
        String transactionRef = UUID.randomUUID().toString();

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getSourceAccountNumber()).matches()) {
                loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                        request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                        request.getDescription(), transactionRef, "FAILED", "Numéro de compte source invalide", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte source invalide\"}");
            }
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getTargetAccountNumber()).matches()) {
                loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                        request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                        request.getDescription(), transactionRef, "FAILED", "Numéro de compte cible invalide", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte cible invalide\"}");
            }

            Account sourceAccount = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte source non trouvé"));

            Account targetAccount = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte cible non trouvé"));

            if (sourceAccount.getId().equals(targetAccount.getId())) {
                loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                        request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                        request.getDescription(), transactionRef, "FAILED", "Les comptes source et cible doivent être différents", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Les comptes source et cible doivent être différents\"}");
            }

            BigDecimal amountBd = request.getAmount();
            if (amountBd.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                        request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                        request.getDescription(), transactionRef, "FAILED", "Le montant doit être supérieur à 0", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}");
            }

            Client sourceClient = clientRepository.findById(sourceAccount.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client source non trouvé"));

            Client targetClient = clientRepository.findById(targetAccount.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client cible non trouvé"));

            String senderPhone = sourceClient.getPhoneNumber();
            String recipientPhone = targetClient.getPhoneNumber();

            accountService.transfer(
                    senderPhone,
                    recipientPhone,
                    amountBd,
                    request.getDescription() != null ? request.getDescription() : "Virement interne"
            );

            loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                    request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                    request.getDescription(), transactionRef, "SUCCESS", null, 0.0, amount, httpServletRequest);

            log.info("Virement interne effectué avec succès");
            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Virement interne effectué\"}");

        } catch (IllegalArgumentException | InsufficientBalanceException e) {
            loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                    request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            loggingService.logTransaction("ADMIN_TRANSFER", "VIREMENT_INTERNE", amount,
                    request.getSourceAccountNumber(), request.getTargetAccountNumber(), null, null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            log.error("Erreur lors du virement interne", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    @PostMapping("/accounts/credit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> creditAccount(@Valid @RequestBody AccountOperationRequest request) {
        log.info("Demande de crédit manuel - Compte: {}, Montant: {}",
                maskAccountNumber(request.getAccountNumber()), request.getAmount());

        Double amount = request.getAmount().doubleValue();
        String transactionRef = UUID.randomUUID().toString();

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getAccountNumber()).matches()) {
                loggingService.logTransaction("ADMIN_CREDIT", "CREDIT_MANUEL", amount,
                        null, request.getAccountNumber(), null, null,
                        request.getDescription(), transactionRef, "FAILED", "Numéro de compte invalide", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte invalide\"}");
            }

            Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte non trouvé"));

            BigDecimal amountBd = request.getAmount();
            if (amountBd.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                loggingService.logTransaction("ADMIN_CREDIT", "CREDIT_MANUEL", amount,
                        null, request.getAccountNumber(), null, null,
                        request.getDescription(), transactionRef, "FAILED", "Le montant doit être supérieur à 0", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}");
            }

            account.setBalance(account.getBalance().add(amountBd));
            accountRepository.save(account);

            Transaction transaction = new Transaction();
            transaction.setAccountId(account.getId());
            transaction.setAmount(amountBd);
            transaction.setType("CREDIT_MANUEL");
            transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Crédit manuel admin");
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            loggingService.logTransaction("ADMIN_CREDIT", "CREDIT_MANUEL", amount,
                    null, request.getAccountNumber(), null, null,
                    request.getDescription(), transactionRef, "SUCCESS", null, 0.0, amount, httpServletRequest);

            log.info("Crédit manuel effectué avec succès");
            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Crédit effectué\"}");

        } catch (IllegalArgumentException e) {
            loggingService.logTransaction("ADMIN_CREDIT", "CREDIT_MANUEL", amount,
                    null, request.getAccountNumber(), null, null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            loggingService.logTransaction("ADMIN_CREDIT", "CREDIT_MANUEL", amount,
                    null, request.getAccountNumber(), null, null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            log.error("Erreur lors du crédit manuel", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    @PostMapping("/accounts/debit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> debitAccount(@Valid @RequestBody AccountOperationRequest request) {
        log.info("Demande de débit manuel - Compte: {}, Montant: {}",
                maskAccountNumber(request.getAccountNumber()), request.getAmount());

        Double amount = request.getAmount().doubleValue();
        String transactionRef = UUID.randomUUID().toString();

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getAccountNumber()).matches()) {
                loggingService.logTransaction("ADMIN_DEBIT", "DEBIT_MANUEL", amount,
                        request.getAccountNumber(), null, null, null,
                        request.getDescription(), transactionRef, "FAILED", "Numéro de compte invalide", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte invalide\"}");
            }

            Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte non trouvé"));

            BigDecimal amountBd = request.getAmount();
            if (amountBd.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                loggingService.logTransaction("ADMIN_DEBIT", "DEBIT_MANUEL", amount,
                        request.getAccountNumber(), null, null, null,
                        request.getDescription(), transactionRef, "FAILED", "Le montant doit être supérieur à 0", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}");
            }

            if (account.getBalance().compareTo(amountBd) < 0) {
                loggingService.logTransaction("ADMIN_DEBIT", "DEBIT_MANUEL", amount,
                        request.getAccountNumber(), null, null, null,
                        request.getDescription(), transactionRef, "FAILED", "Solde insuffisant", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Solde insuffisant\"}");
            }

            account.setBalance(account.getBalance().subtract(amountBd));
            accountRepository.save(account);

            Transaction transaction = new Transaction();
            transaction.setAccountId(account.getId());
            transaction.setAmount(amountBd);
            transaction.setType("DEBIT_MANUEL");
            transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Débit manuel admin");
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            loggingService.logTransaction("ADMIN_DEBIT", "DEBIT_MANUEL", amount,
                    request.getAccountNumber(), null, null, null,
                    request.getDescription(), transactionRef, "SUCCESS", null, 0.0, amount, httpServletRequest);

            log.info("Débit manuel effectué avec succès");
            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Débit effectué\"}");

        } catch (IllegalArgumentException e) {
            loggingService.logTransaction("ADMIN_DEBIT", "DEBIT_MANUEL", amount,
                    request.getAccountNumber(), null, null, null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            loggingService.logTransaction("ADMIN_DEBIT", "DEBIT_MANUEL", amount,
                    request.getAccountNumber(), null, null, null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            log.error("Erreur lors du débit manuel", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    
    // ENDPOINT CORRIGÉ : Débit depuis téléphone avec gestion multi-comptes
    

    @PostMapping("/accounts/debit-from-phone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> debitFromPhone(@RequestBody DebitFromPhoneRequest request) {
        log.info("Débit depuis téléphone - Phone: {}, Montant: {}",
                maskPhoneNumber(request.getPhoneNumber()), request.getAmount());

        Double amount = request.getAmount();
        String transactionRef = UUID.randomUUID().toString();

        try {
            // Validation du numéro de téléphone
            if (!PHONE_NUMBER_PATTERN.matcher(request.getPhoneNumber()).matches()) {
                loggingService.logTransaction("ADMIN_DEBIT_FROM_PHONE", "DEBIT_MANUEL", amount,
                        null, null, request.getPhoneNumber(), null,
                        request.getDescription(), transactionRef, "FAILED", "Numéro de téléphone invalide", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Numéro de téléphone invalide"));
            }

            // Rechercher le client par téléphone
            Client client = clientRepository.findByPhoneNumber(request.getPhoneNumber())
                    .orElseThrow(() -> new RuntimeException("Client non trouvé: " + request.getPhoneNumber()));

            
            //  GESTION MULTI-COMPTES : Récupérer tous les comptes du client
            
            List<Account> accounts = accountRepository.findAllByClientId(client.getId());

            if (accounts.isEmpty()) {
                loggingService.logTransaction("ADMIN_DEBIT_FROM_PHONE", "DEBIT_MANUEL", amount,
                        null, null, request.getPhoneNumber(), null,
                        request.getDescription(), transactionRef, "FAILED", "Aucun compte trouvé pour ce client", 0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Aucun compte trouvé pour ce client"));
            }

            
            // 🔒 Sélectionner le compte (avec priorité au compte spécifié ou premier compte actif)
            
            Account account;

            // Si un compte est spécifié dans la requête
            if (request.getAccountNumber() != null && !request.getAccountNumber().isEmpty()) {
                final String finalAccountNumber = request.getAccountNumber();
                account = accounts.stream()
                        .filter(a -> a.getAccountNumber().equalsIgnoreCase(finalAccountNumber))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException(
                                String.format("Compte %s non trouvé pour ce client. Comptes disponibles: %s",
                                        finalAccountNumber,
                                        accounts.stream().map(Account::getAccountNumber).collect(Collectors.joining(", ")))));
                log.info("Compte spécifié trouvé: {}", account.getAccountNumber());
            } else if (accounts.size() == 1) {
                // Un seul compte → l'utiliser
                account = accounts.get(0);
                log.info("Un seul compte trouvé: {}", account.getAccountNumber());
            } else {
                // Plusieurs comptes → l'utilisateur doit spécifier lequel
                String accountList = accounts.stream()
                        .map(Account::getAccountNumber)
                        .collect(Collectors.joining(", "));
                loggingService.logTransaction("ADMIN_DEBIT_FROM_PHONE", "DEBIT_MANUEL", amount,
                        null, null, request.getPhoneNumber(), null,
                        request.getDescription(), transactionRef, "FAILED",
                        "Plusieurs comptes trouvés. Veuillez spécifier le compte: " + accountList,
                        0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", "Plusieurs comptes trouvés. Veuillez spécifier le compte.",
                                "accounts", accounts.stream().map(Account::getAccountNumber).collect(Collectors.toList())
                        ));
            }

            BigDecimal amountBd = BigDecimal.valueOf(request.getAmount());

            // Vérifier le solde
            if (account.getBalance().compareTo(amountBd) < 0) {
                loggingService.logTransaction("ADMIN_DEBIT_FROM_PHONE", "DEBIT_MANUEL", amount,
                        account.getAccountNumber(), null, request.getPhoneNumber(), null,
                        request.getDescription(), transactionRef, "FAILED",
                        "Solde insuffisant sur le compte " + account.getAccountNumber(),
                        0.0, amount, httpServletRequest);
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", String.format("Solde insuffisant sur le compte %s. Solde actuel: %d FCFA",
                                        account.getAccountNumber(),
                                        account.getBalance().longValue())));
            }

            // Débiter le compte
            account.setBalance(account.getBalance().subtract(amountBd));
            accountRepository.save(account);

            // Enregistrer la transaction
            Transaction transaction = new Transaction();
            transaction.setAccountId(account.getId());
            transaction.setAmount(amountBd);
            transaction.setType("DEBIT_MANUEL");
            transaction.setDescription(String.format("%s - Compte: %s",
                    request.getDescription() != null ? request.getDescription() : "Débit manuel admin depuis téléphone",
                    account.getAccountNumber()));
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            // Log de succès
            loggingService.logTransaction("ADMIN_DEBIT_FROM_PHONE", "DEBIT_MANUEL", amount,
                    account.getAccountNumber(), null, request.getPhoneNumber(), null,
                    request.getDescription(), transactionRef, "SUCCESS", null, 0.0, amount, httpServletRequest);

            log.info("Débit depuis téléphone effectué avec succès - Phone: {}, Compte: {}, Montant: {}",
                    maskPhoneNumber(request.getPhoneNumber()),
                    account.getAccountNumber(),
                    amount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Débit effectué avec succès depuis le compte %s", account.getAccountNumber()),
                    "accountNumber", account.getAccountNumber(),
                    "newBalance", account.getBalance().doubleValue()
            ));

        } catch (Exception e) {
            loggingService.logTransaction("ADMIN_DEBIT_FROM_PHONE", "DEBIT_MANUEL", amount,
                    null, null, request.getPhoneNumber(), null,
                    request.getDescription(), transactionRef, "FAILED", e.getMessage(), 0.0, amount, httpServletRequest);
            log.error("Erreur débit depuis téléphone", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < AppConstants.MIN_ACCOUNT_NUMBER_LENGTH) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - AppConstants.MIN_ACCOUNT_NUMBER_LENGTH);
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "****";
        }
        return phoneNumber.substring(0, Math.min(4, phoneNumber.length())) + "****";
    }

    
    // DTO pour debit-from-phone (avec numéro de compte optionnel)
    

    public static class DebitFromPhoneRequest {
        private String phoneNumber;
        private Double amount;
        private String description;
        private String accountNumber;  // ← NOUVEAU : champ pour spécifier le compte

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    }
}
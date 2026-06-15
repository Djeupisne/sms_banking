package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.dto.request.TransferRequestDto;
import com.orabank.smsbanking.dto.request.InternalTransferRequest;
import com.orabank.smsbanking.dto.request.AccountOperationRequest;
import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;  // AJOUTEZ CET IMPORT
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.service.AccountService;  // AJOUTEZ CET IMPORT
import com.orabank.smsbanking.service.MobileMoneyService;
import com.orabank.smsbanking.util.AppConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MobileMoneyService mobileMoneyService;
    private final AccountService accountService;  // AJOUTEZ CETTE LIGNE

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

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(transferRequest.getAccountNumber()).matches()) {
                log.warn("Tentative de virement avec numéro de compte invalide");
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
                log.warn("Tentative de virement avec numéro de téléphone invalide");
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de téléphone invalide\"}");
            }

            BigDecimal amount = transferRequest.getAmount();
            if (amount.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                log.warn("Tentative de virement inférieur au minimum: {}", amount);
                return ResponseEntity.badRequest()
                        .body(String.format("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}"));
            }

            if (amount.compareTo(new BigDecimal(AppConstants.MAX_TRANSFER_AMOUNT)) > 0) {
                log.warn("Tentative de virement supérieur au maximum: {}", amount);
                return ResponseEntity.badRequest()
                        .body(String.format("{\"status\": \"error\", \"message\": \"Montant maximum: %d FCFA\"}",
                                AppConstants.MAX_TRANSFER_AMOUNT));
            }

            boolean success = mobileMoneyService.transferToMobileMoney(senderPhone, amount);

            if (!success) {
                log.warn("Virement Mobile Money échoué - Source: {}, Montant: {}",
                        maskPhoneNumber(senderPhone), amount);
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Solde insuffisant \"}");
            }

            log.info("Virement Mobile Money effectué avec succès - Source: {}, Destinataire: {}, Montant: {}",
                    maskPhoneNumber(senderPhone), maskPhoneNumber(transferRequest.getRecipientPhone()), amount);
            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Virement effectué\"}");

        } catch (IllegalArgumentException e) {
            log.warn("Erreur de validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur lors du virement Mobile Money", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    /**
     * Effectue un virement interne entre deux comptes.
     */
    @PostMapping("/transfers/internal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> internalTransfer(@Valid @RequestBody InternalTransferRequest request) {
        log.info("Demande de virement interne - Source: {}, Cible: {}, Montant: {}",
                maskAccountNumber(request.getSourceAccountNumber()),
                maskAccountNumber(request.getTargetAccountNumber()),
                request.getAmount());

        try {
            // Validation des numéros de compte
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getSourceAccountNumber()).matches()) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte source invalide\"}");
            }
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getTargetAccountNumber()).matches()) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte cible invalide\"}");
            }

            // Trouver les comptes
            Account sourceAccount = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte source non trouvé"));

            Account targetAccount = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte cible non trouvé"));

            // Vérifier que ce ne sont pas les mêmes comptes
            if (sourceAccount.getId().equals(targetAccount.getId())) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Les comptes source et cible doivent être différents\"}");
            }

            // Validation du montant
            BigDecimal amount = request.getAmount();
            if (amount.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}");
            }

            // Récupérer les clients associés
            Client sourceClient = clientRepository.findById(sourceAccount.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client source non trouvé"));

            Client targetClient = clientRepository.findById(targetAccount.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client cible non trouvé"));

            // Utiliser les numéros de téléphone des clients
            String senderPhone = sourceClient.getPhoneNumber();
            String recipientPhone = targetClient.getPhoneNumber();

            // Effectuer le virement avec frais via AccountService
            accountService.transfer(
                    senderPhone,
                    recipientPhone,
                    amount,
                    request.getDescription() != null ? request.getDescription() : "Virement interne"
            );

            log.info("Virement interne effectué avec succès - Source: {}, Cible: {}, Montant: {}",
                    maskAccountNumber(request.getSourceAccountNumber()),
                    maskAccountNumber(request.getTargetAccountNumber()),
                    amount);

            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Virement interne effectué\"}");

        } catch (IllegalArgumentException e) {
            log.warn("Erreur de validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (InsufficientBalanceException e) {
            log.warn("Solde insuffisant: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur lors du virement interne", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    /**
     * Crédite manuellement un compte (opération admin).
     */
    @PostMapping("/accounts/credit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> creditAccount(@Valid @RequestBody AccountOperationRequest request) {
        log.info("Demande de crédit manuel - Compte: {}, Montant: {}",
                maskAccountNumber(request.getAccountNumber()),
                request.getAmount());

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getAccountNumber()).matches()) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte invalide\"}");
            }

            Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte non trouvé"));

            BigDecimal amount = request.getAmount();
            if (amount.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}");
            }

            account.setBalance(account.getBalance().add(amount));
            accountRepository.save(account);

            Transaction transaction = new Transaction();
            transaction.setAccountId(account.getId());
            transaction.setAmount(amount);
            transaction.setType("CREDIT_MANUEL");
            transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Crédit manuel admin");
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            log.info("Crédit manuel effectué avec succès - Compte: {}, Montant: {}",
                    maskAccountNumber(request.getAccountNumber()), amount);

            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Crédit effectué\"}");

        } catch (IllegalArgumentException e) {
            log.warn("Erreur de validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur lors du crédit manuel", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
        }
    }

    /**
     * Débite manuellement un compte (opération admin).
     */
    @PostMapping("/accounts/debit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> debitAccount(@Valid @RequestBody AccountOperationRequest request) {
        log.info("Demande de débit manuel - Compte: {}, Montant: {}",
                maskAccountNumber(request.getAccountNumber()),
                request.getAmount());

        try {
            if (!ACCOUNT_NUMBER_PATTERN.matcher(request.getAccountNumber()).matches()) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Numéro de compte invalide\"}");
            }

            Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Compte non trouvé"));

            BigDecimal amount = request.getAmount();
            if (amount.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Le montant doit être supérieur à 0\"}");
            }

            if (account.getBalance().compareTo(amount) < 0) {
                return ResponseEntity.badRequest()
                        .body("{\"status\": \"error\", \"message\": \"Solde insuffisant\"}");
            }

            account.setBalance(account.getBalance().subtract(amount));
            accountRepository.save(account);

            Transaction transaction = new Transaction();
            transaction.setAccountId(account.getId());
            transaction.setAmount(amount);
            transaction.setType("DEBIT_MANUEL");
            transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Débit manuel admin");
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            log.info("Débit manuel effectué avec succès - Compte: {}, Montant: {}",
                    maskAccountNumber(request.getAccountNumber()), amount);

            return ResponseEntity.ok().body("{\"status\": \"success\", \"message\": \"Débit effectué\"}");

        } catch (IllegalArgumentException e) {
            log.warn("Erreur de validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(String.format("{\"status\": \"error\", \"message\": \"%s\"}", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur lors du débit manuel", e);
            return ResponseEntity.badRequest()
                    .body("{\"status\": \"error\", \"message\": \"Erreur lors du traitement\"}");
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
}
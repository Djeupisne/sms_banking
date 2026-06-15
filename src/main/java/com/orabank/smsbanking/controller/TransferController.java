package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.service.MobileMoneyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final MobileMoneyService mobileMoneyService;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("500000");
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("10");

    // ============================================================
    // 1. VIREMENT INTERNE (COMPTE → COMPTE) - 0% frais
    // ============================================================
    @PostMapping("/internal")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> internalTransfer(@RequestBody InternalTransferRequest request) {
        log.info("Virement interne - Source: {}, Target: {}, Amount: {} FCFA",
                request.getSourceAccountNumber(), request.getTargetAccountNumber(), request.getAmount());

        Map<String, Object> response = new HashMap<>();

        try {
            BigDecimal amount = new BigDecimal(request.getAmount());

            if (amount.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(response);
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                response.put("success", false);
                response.put("message", "Le montant doit être supérieur à 0");
                return ResponseEntity.badRequest().body(response);
            }

            Account sourceAccount = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte source non trouvé: " + request.getSourceAccountNumber()));

            Account targetAccount = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte cible non trouvé: " + request.getTargetAccountNumber()));

            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                response.put("success", false);
                response.put("message", "Solde insuffisant");
                return ResponseEntity.badRequest().body(response);
            }

            // Effectuer le virement
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
            targetAccount.setBalance(targetAccount.getBalance().add(amount));

            accountRepository.save(sourceAccount);
            accountRepository.save(targetAccount);

            // Transaction de débit
            Transaction debitTransaction = new Transaction();
            debitTransaction.setAccountId(sourceAccount.getId());
            debitTransaction.setAmount(amount);
            debitTransaction.setType("VIREMENT_INTERNE");
            debitTransaction.setStatus(TransactionStatus.COMPLETED);
            debitTransaction.setDescription(request.getDescription() != null ? request.getDescription() : "Virement interne vers " + request.getTargetAccountNumber());
            debitTransaction.setRelatedAccountId(targetAccount.getId());
            debitTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(debitTransaction);

            // Transaction de crédit
            Transaction creditTransaction = new Transaction();
            creditTransaction.setAccountId(targetAccount.getId());
            creditTransaction.setAmount(amount);
            creditTransaction.setType("CREDIT");
            creditTransaction.setStatus(TransactionStatus.COMPLETED);
            creditTransaction.setDescription("Virement interne depuis " + request.getSourceAccountNumber());
            creditTransaction.setRelatedAccountId(sourceAccount.getId());
            creditTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(creditTransaction);

            response.put("success", true);
            response.put("message", "Virement interne effectué avec succès");
            response.put("amount", request.getAmount());
            response.put("fees", 0);
            response.put("total", request.getAmount());
            response.put("sourceAccount", request.getSourceAccountNumber());
            response.put("targetAccount", request.getTargetAccountNumber());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du virement interne", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ============================================================
    // 2. VIREMENT INTERNE (Téléphone Orabank → COMPTE) - 10% frais
    // ============================================================
    @PostMapping("/internal/from-phone")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> internalTransferFromPhone(@RequestBody InternalFromPhoneRequest request) {
        log.info("Virement interne depuis téléphone - Source Phone: {}, Target Account: {}, Amount: {} FCFA",
                request.getSourcePhone(), request.getTargetAccountNumber(), request.getAmount());

        Map<String, Object> response = new HashMap<>();

        try {
            BigDecimal amount = new BigDecimal(request.getAmount());
            BigDecimal fees = amount.multiply(FEE_PERCENTAGE).divide(new BigDecimal("100"), 0, RoundingMode.CEILING);
            BigDecimal totalAmount = amount.add(fees);

            // Générer une référence commune pour lier les transactions
            String commonReference = UUID.randomUUID().toString();

            if (amount.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(response);
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                response.put("success", false);
                response.put("message", "Le montant doit être supérieur à 0");
                return ResponseEntity.badRequest().body(response);
            }

            // Vérifier le client source
            Client sourceClient = clientRepository.findByPhoneNumber(request.getSourcePhone())
                    .orElseThrow(() -> new RuntimeException("Client Orabank non trouvé avec ce numéro: " + request.getSourcePhone()));

            Account sourceAccount = accountRepository.findByClientId(sourceClient.getId())
                    .orElseThrow(() -> new RuntimeException("Compte non trouvé pour ce client: " + request.getSourcePhone()));

            Account targetAccount = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte cible non trouvé: " + request.getTargetAccountNumber()));

            if (sourceAccount.getBalance().compareTo(totalAmount) < 0) {
                response.put("success", false);
                response.put("message", "Solde insuffisant. Total requis: " + totalAmount + " FCFA (dont " + fees + " FCFA de frais)");
                return ResponseEntity.badRequest().body(response);
            }

            // Débiter le compte source (montant + frais)
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmount));
            accountRepository.save(sourceAccount);

            // Créditer le compte cible (montant uniquement)
            targetAccount.setBalance(targetAccount.getBalance().add(amount));
            accountRepository.save(targetAccount);

            // Transaction de débit (montant net uniquement)
            Transaction debitTransaction = new Transaction();
            debitTransaction.setAccountId(sourceAccount.getId());
            debitTransaction.setAmount(amount);  // ← Montant net (sans frais)
            debitTransaction.setType("VIREMENT_INTERNE");
            debitTransaction.setStatus(TransactionStatus.COMPLETED);
            debitTransaction.setReference(commonReference);
            debitTransaction.setDescription("Virement depuis " + request.getSourcePhone() + " vers " + request.getTargetAccountNumber());
            debitTransaction.setRelatedAccountId(targetAccount.getId());
            debitTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(debitTransaction);

            // Transaction de crédit
            Transaction creditTransaction = new Transaction();
            creditTransaction.setAccountId(targetAccount.getId());
            creditTransaction.setAmount(amount);
            creditTransaction.setType("CREDIT");
            creditTransaction.setStatus(TransactionStatus.COMPLETED);
            creditTransaction.setReference(commonReference);
            creditTransaction.setDescription("Virement depuis " + request.getSourcePhone());
            creditTransaction.setRelatedAccountId(sourceAccount.getId());
            creditTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(creditTransaction);

            // Transaction de frais avec la même référence
            if (fees.compareTo(BigDecimal.ZERO) > 0) {
                Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
                if (feesAccount != null) {
                    feesAccount.setBalance(feesAccount.getBalance().add(fees));
                    accountRepository.save(feesAccount);

                    Transaction feesTransaction = new Transaction();
                    feesTransaction.setAccountId(feesAccount.getId());
                    feesTransaction.setAmount(fees);
                    feesTransaction.setType("CREDIT_FEES");
                    feesTransaction.setStatus(TransactionStatus.COMPLETED);
                    feesTransaction.setReference(commonReference);
                    feesTransaction.setDescription("Frais virement interne (10%) - " + amount + " FCFA");
                    feesTransaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(feesTransaction);
                }
            }

            response.put("success", true);
            response.put("message", "Virement interne effectué avec succès");
            response.put("amount", request.getAmount());
            response.put("fees", fees.intValue());
            response.put("total", totalAmount.intValue());
            response.put("sourcePhone", request.getSourcePhone());
            response.put("targetAccount", request.getTargetAccountNumber());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du virement interne depuis téléphone", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ============================================================
    // 3. TRANSFERT MOBILE MONEY (COMPTE → Téléphone) - 10% frais
    // ============================================================
    @PostMapping("/mobile-money/from-account")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> transferFromAccountToMobileMoney(@RequestBody MobileMoneyTransferRequest request) {
        log.info("Transfert compte vers Mobile Money - Account: {}, Amount: {} FCFA, Recipient: {}",
                request.getAccountNumber(), request.getAmount(), request.getRecipientPhone());

        Map<String, Object> response = new HashMap<>();

        try {
            BigDecimal amount = new BigDecimal(request.getAmount());
            BigDecimal fees = amount.multiply(FEE_PERCENTAGE).divide(new BigDecimal("100"), 0, RoundingMode.CEILING);
            BigDecimal totalAmount = amount.add(fees);

            String commonReference = UUID.randomUUID().toString();

            if (amount.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(response);
            }

            Account sourceAccount = accountRepository.findByAccountNumber(request.getAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte source non trouvé: " + request.getAccountNumber()));

            if (sourceAccount.getBalance().compareTo(totalAmount) < 0) {
                response.put("success", false);
                response.put("message", "Solde insuffisant. Total requis: " + totalAmount + " FCFA (dont " + fees + " FCFA de frais)");
                return ResponseEntity.badRequest().body(response);
            }

            Client sourceClient = clientRepository.findById(sourceAccount.getClientId())
                    .orElseThrow(() -> new RuntimeException("Client source non trouvé"));

            boolean success = mobileMoneyService.transferToMobileMoney(sourceClient.getPhoneNumber(), amount);

            if (success) {
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmount));
                accountRepository.save(sourceAccount);

                Transaction debitTransaction = new Transaction();
                debitTransaction.setAccountId(sourceAccount.getId());
                debitTransaction.setAmount(amount);  // Montant net
                debitTransaction.setType("DEBIT_MOBILE_MONEY");
                debitTransaction.setStatus(TransactionStatus.COMPLETED);
                debitTransaction.setReference(commonReference);
                debitTransaction.setDescription("Transfert Mobile Money vers " + request.getRecipientPhone());
                debitTransaction.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(debitTransaction);

                Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
                if (feesAccount != null && fees.compareTo(BigDecimal.ZERO) > 0) {
                    feesAccount.setBalance(feesAccount.getBalance().add(fees));
                    accountRepository.save(feesAccount);

                    Transaction feesTransaction = new Transaction();
                    feesTransaction.setAccountId(feesAccount.getId());
                    feesTransaction.setAmount(fees);
                    feesTransaction.setType("CREDIT_FEES");
                    feesTransaction.setStatus(TransactionStatus.COMPLETED);
                    feesTransaction.setReference(commonReference);
                    feesTransaction.setDescription("Frais Mobile Money (10%) - " + amount + " FCFA");
                    feesTransaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(feesTransaction);
                }

                response.put("success", true);
                response.put("message", "Transfert Mobile Money effectué avec succès");
                response.put("amount", request.getAmount());
                response.put("fees", fees.intValue());
                response.put("total", totalAmount.intValue());
                response.put("recipientPhone", request.getRecipientPhone());
            } else {
                response.put("success", false);
                response.put("message", "Échec du transfert Mobile Money");
                return ResponseEntity.badRequest().body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du transfert Mobile Money", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ============================================================
    // 4. TRANSFERT MOBILE MONEY (Téléphone Orabank → Téléphone) - 10% frais
    // ============================================================
    @PostMapping("/mobile-money/from-phone")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> transferFromPhoneToMobileMoney(@RequestBody MobileMoneyFromPhoneRequest request) {
        log.info("Transfert téléphone vers Mobile Money - Source: {}, Amount: {} FCFA, Recipient: {}",
                request.getSourcePhone(), request.getAmount(), request.getRecipientPhone());

        Map<String, Object> response = new HashMap<>();

        try {
            BigDecimal amount = new BigDecimal(request.getAmount());
            BigDecimal fees = amount.multiply(FEE_PERCENTAGE).divide(new BigDecimal("100"), 0, RoundingMode.CEILING);
            BigDecimal totalAmount = amount.add(fees);

            String commonReference = UUID.randomUUID().toString();

            if (amount.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(response);
            }

            Client sourceClient = clientRepository.findByPhoneNumber(request.getSourcePhone())
                    .orElseThrow(() -> new RuntimeException("Client Orabank non trouvé: " + request.getSourcePhone()));

            Account sourceAccount = accountRepository.findByClientId(sourceClient.getId())
                    .orElseThrow(() -> new RuntimeException("Compte non trouvé pour ce client"));

            if (sourceAccount.getBalance().compareTo(totalAmount) < 0) {
                response.put("success", false);
                response.put("message", "Solde insuffisant. Total requis: " + totalAmount + " FCFA (dont " + fees + " FCFA de frais)");
                return ResponseEntity.badRequest().body(response);
            }

            boolean success = mobileMoneyService.transferToMobileMoney(sourceClient.getPhoneNumber(), amount);

            if (success) {
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmount));
                accountRepository.save(sourceAccount);

                Transaction debitTransaction = new Transaction();
                debitTransaction.setAccountId(sourceAccount.getId());
                debitTransaction.setAmount(amount);  // Montant net
                debitTransaction.setType("DEBIT_MOBILE_MONEY");
                debitTransaction.setStatus(TransactionStatus.COMPLETED);
                debitTransaction.setReference(commonReference);
                debitTransaction.setDescription("Transfert Mobile Money de " + request.getSourcePhone() + " vers " + request.getRecipientPhone());
                debitTransaction.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(debitTransaction);

                Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
                if (feesAccount != null && fees.compareTo(BigDecimal.ZERO) > 0) {
                    feesAccount.setBalance(feesAccount.getBalance().add(fees));
                    accountRepository.save(feesAccount);

                    Transaction feesTransaction = new Transaction();
                    feesTransaction.setAccountId(feesAccount.getId());
                    feesTransaction.setAmount(fees);
                    feesTransaction.setType("CREDIT_FEES");
                    feesTransaction.setStatus(TransactionStatus.COMPLETED);
                    feesTransaction.setReference(commonReference);
                    feesTransaction.setDescription("Frais Mobile Money (10%) - " + amount + " FCFA");
                    feesTransaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(feesTransaction);
                }

                response.put("success", true);
                response.put("message", "Transfert Mobile Money effectué avec succès");
                response.put("amount", request.getAmount());
                response.put("fees", fees.intValue());
                response.put("total", totalAmount.intValue());
                response.put("sourcePhone", request.getSourcePhone());
                response.put("recipientPhone", request.getRecipientPhone());
            } else {
                response.put("success", false);
                response.put("message", "Échec du transfert Mobile Money");
                return ResponseEntity.badRequest().body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du transfert Mobile Money", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ============================================================
    // DTOs
    // ============================================================

    @Data
    public static class InternalTransferRequest {
        private String sourceAccountNumber;
        private String targetAccountNumber;
        private Double amount;
        private String description;
    }

    @Data
    public static class InternalFromPhoneRequest {
        private String sourcePhone;
        private String targetAccountNumber;
        private Double amount;
        private String description;
    }

    @Data
    public static class MobileMoneyTransferRequest {
        private String accountNumber;
        private Double amount;
        private String recipientPhone;
        private String description;
    }

    @Data
    public static class MobileMoneyFromPhoneRequest {
        private String sourcePhone;
        private Double amount;
        private String recipientPhone;
        private String description;
    }
}
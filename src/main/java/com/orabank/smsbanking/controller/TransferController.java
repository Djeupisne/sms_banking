package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.service.MobileMoneyService;
import com.orabank.smsbanking.service.TransactionLoggingService;
import com.orabank.smsbanking.util.LoggingUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final MobileMoneyService mobileMoneyService;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionLoggingService loggingService;
    private final HttpServletRequest httpServletRequest;

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("500000");
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("10");

    /**
     * Vérifie si l'utilisateur actuel est un ADMIN
     */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Masque les informations sensibles pour les utilisateurs non-admin
     */
    private Map<String, Object> maskSensitiveInfo(Map<String, Object> response) {
        if (!isAdmin()) {
            // Masquer les numéros de téléphone
            if (response.containsKey("sourcePhone")) {
                String phone = (String) response.get("sourcePhone");
                response.put("sourcePhone", LoggingUtil.maskPhoneNumber(phone));
            }
            if (response.containsKey("recipientPhone")) {
                String phone = (String) response.get("recipientPhone");
                response.put("recipientPhone", LoggingUtil.maskPhoneNumber(phone));
            }
            if (response.containsKey("targetPhone")) {
                String phone = (String) response.get("targetPhone");
                response.put("targetPhone", LoggingUtil.maskPhoneNumber(phone));
            }
            // Masquer les numéros de compte partiellement
            if (response.containsKey("sourceAccount")) {
                String account = (String) response.get("sourceAccount");
                response.put("sourceAccount", maskAccountNumber(account));
            }
            if (response.containsKey("targetAccount")) {
                String account = (String) response.get("targetAccount");
                response.put("targetAccount", maskAccountNumber(account));
            }
        }
        return response;
    }

    /**
     * Masque partiellement un numéro de compte
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int length = accountNumber.length();
        String first = accountNumber.substring(0, 4);
        String last = accountNumber.substring(length - 4);
        return first + "****" + last;
    }

    
    // 1. VIREMENT INTERNE (COMPTE → COMPTE) - 0% frais


    @PostMapping("/internal")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> internalTransfer(@RequestBody InternalTransferRequest request) {
        log.info("Virement interne - Source: {}, Target: {}, Amount: {} FCFA",
                request.getSourceAccountNumber(), request.getTargetAccountNumber(), request.getAmount());

        Map<String, Object> response = new HashMap<>();
        Double amount = request.getAmount();
        String transactionRef = UUID.randomUUID().toString();

        try {
            BigDecimal amountBd = new BigDecimal(request.getAmount());

            if (amountBd.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(response);
            }

            if (amountBd.compareTo(BigDecimal.ZERO) <= 0) {
                response.put("success", false);
                response.put("message", "Le montant doit être supérieur à 0");
                return ResponseEntity.badRequest().body(response);
            }

            Account sourceAccount = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte source non trouvé: " + request.getSourceAccountNumber()));

            Account targetAccount = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte cible non trouvé: " + request.getTargetAccountNumber()));

            if (sourceAccount.getBalance().compareTo(amountBd) < 0) {
                response.put("success", false);
                response.put("message", "Solde insuffisant");
                return ResponseEntity.badRequest().body(response);
            }

            // Effectuer le virement
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(amountBd));
            targetAccount.setBalance(targetAccount.getBalance().add(amountBd));

            accountRepository.save(sourceAccount);
            accountRepository.save(targetAccount);

            // Transaction de débit
            Transaction debitTransaction = new Transaction();
            debitTransaction.setAccountId(sourceAccount.getId());
            debitTransaction.setAmount(amountBd);
            debitTransaction.setType("VIREMENT_INTERNE");
            debitTransaction.setStatus(TransactionStatus.COMPLETED);
            debitTransaction.setDescription(request.getDescription() != null ? request.getDescription() : "Virement interne vers " + request.getTargetAccountNumber());
            debitTransaction.setRelatedAccountId(targetAccount.getId());
            debitTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(debitTransaction);

            // Transaction de crédit
            Transaction creditTransaction = new Transaction();
            creditTransaction.setAccountId(targetAccount.getId());
            creditTransaction.setAmount(amountBd);
            creditTransaction.setType("CREDIT");
            creditTransaction.setStatus(TransactionStatus.COMPLETED);
            creditTransaction.setDescription("Virement interne depuis " + request.getSourceAccountNumber());
            creditTransaction.setRelatedAccountId(sourceAccount.getId());
            creditTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(creditTransaction);

            // LOG DE SUCCÈS
            loggingService.logTransaction(
                    "INTERNAL_TRANSFER", "VIREMENT_INTERNE", amount,
                    request.getSourceAccountNumber(), request.getTargetAccountNumber(),
                    null, null,
                    request.getDescription(), transactionRef,
                    "SUCCESS", null, 0.0, amount, httpServletRequest
            );

            response.put("success", true);
            response.put("message", "Virement interne effectué avec succès");
            response.put("amount", request.getAmount());
            response.put("fees", 0);
            response.put("total", request.getAmount());
            response.put("sourceAccount", request.getSourceAccountNumber());
            response.put("targetAccount", request.getTargetAccountNumber());

            // Masquer les informations sensibles
            return ResponseEntity.ok(maskSensitiveInfo(response));

        } catch (Exception e) {
            loggingService.logTransaction(
                    "INTERNAL_TRANSFER", "VIREMENT_INTERNE", amount,
                    request.getSourceAccountNumber(), request.getTargetAccountNumber(),
                    null, null,
                    request.getDescription(), transactionRef,
                    "FAILED", e.getMessage(), 0.0, amount, httpServletRequest
            );
            log.error("Erreur lors du virement interne", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
        }
    }


    // 2. VIREMENT INTERNE (Téléphone Orabank → COMPTE) - 10% frais


    @PostMapping("/internal/from-phone")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> internalTransferFromPhone(@RequestBody InternalFromPhoneRequest request) {
        log.info("Virement interne depuis téléphone - Phone: {}, Source Account: {}, Target: {}, Amount: {}",
                request.getSourcePhone(), request.getSourceAccountNumber(),
                request.getTargetAccountNumber(), request.getAmount());

        Map<String, Object> response = new HashMap<>();
        Double amount = request.getAmount();

        //  Calcul des frais (10% du montant envoyé)
        Double fees = Math.ceil(amount * 0.1);
        Double totalAmount = amount + fees;
        String transactionRef = UUID.randomUUID().toString();

        try {
            BigDecimal amountBd = new BigDecimal(request.getAmount());
            BigDecimal feesBd = new BigDecimal(fees);
            BigDecimal totalAmountBd = new BigDecimal(totalAmount);

            // Validation du montant
            if (amountBd.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(response);
            }

            if (amountBd.compareTo(BigDecimal.ZERO) <= 0) {
                response.put("success", false);
                response.put("message", "Le montant doit être supérieur à 0");
                return ResponseEntity.badRequest().body(response);
            }

            // Vérifier le client
            Client sourceClient = clientRepository.findByPhoneNumber(request.getSourcePhone())
                    .orElseThrow(() -> new RuntimeException("Client Orabank non trouvé avec ce numéro: " + request.getSourcePhone()));

            // Récupérer le compte source spécifique (si fourni)
            Account sourceAccount;
            if (request.getSourceAccountNumber() != null && !request.getSourceAccountNumber().isEmpty()) {
                sourceAccount = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                        .orElseThrow(() -> new RuntimeException("Compte source non trouvé: " + request.getSourceAccountNumber()));

                if (!sourceAccount.getClientId().equals(sourceClient.getId())) {
                    throw new RuntimeException("Ce compte n'appartient pas à ce client");
                }
            } else {
                List<Account> clientAccounts = accountRepository.findByClientIdAndActiveTrue(sourceClient.getId());
                if (clientAccounts.isEmpty()) {
                    throw new RuntimeException("Aucun compte actif trouvé pour ce client");
                }
                sourceAccount = clientAccounts.get(0);
                log.warn("Aucun compte source spécifié, utilisation du compte par défaut: {}", sourceAccount.getAccountNumber());
            }

            // Récupérer le compte cible
            Account targetAccount = accountRepository.findByAccountNumber(request.getTargetAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte cible non trouvé: " + request.getTargetAccountNumber()));

            //  Vérifier le solde (montant + frais)
            if (sourceAccount.getBalance().compareTo(totalAmountBd) < 0) {
                response.put("success", false);
                response.put("message", String.format(
                        "Solde insuffisant. Solde: %d FCFA, Montant à envoyer: %d FCFA, Frais: %d FCFA, Total requis: %d FCFA",
                        sourceAccount.getBalance().longValue(),
                        amount.longValue(),
                        fees.longValue(),
                        totalAmount.longValue()
                ));
                return ResponseEntity.badRequest().body(response);
            }

            String commonReference = UUID.randomUUID().toString();

            //  Débiter le compte source (montant + frais)
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmountBd));
            accountRepository.save(sourceAccount);

            //  Créditer le compte cible (montant uniquement)
            targetAccount.setBalance(targetAccount.getBalance().add(amountBd));
            accountRepository.save(targetAccount);

            //  Transaction de débit (montant total)
            Transaction debitTransaction = new Transaction();
            debitTransaction.setAccountId(sourceAccount.getId());
            debitTransaction.setAmount(totalAmountBd); //  Total débité
            debitTransaction.setType("VIREMENT_INTERNE");
            debitTransaction.setStatus(TransactionStatus.COMPLETED);
            debitTransaction.setReference(commonReference);
            debitTransaction.setDescription(String.format(
                    "Virement de %d FCFA (dont frais: %d FCFA) depuis %s vers %s",
                    amount.longValue(),
                    fees.longValue(),
                    request.getSourcePhone(),
                    request.getTargetAccountNumber()
            ));
            debitTransaction.setRelatedAccountId(targetAccount.getId());
            debitTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(debitTransaction);

            // Transaction de crédit
            Transaction creditTransaction = new Transaction();
            creditTransaction.setAccountId(targetAccount.getId());
            creditTransaction.setAmount(amountBd);
            creditTransaction.setType("CREDIT");
            creditTransaction.setStatus(TransactionStatus.COMPLETED);
            creditTransaction.setReference(commonReference);
            creditTransaction.setDescription("Virement depuis " + request.getSourcePhone());
            creditTransaction.setRelatedAccountId(sourceAccount.getId());
            creditTransaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(creditTransaction);

            //  Transaction de frais
            if (feesBd.compareTo(BigDecimal.ZERO) > 0) {
                Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
                if (feesAccount != null) {
                    feesAccount.setBalance(feesAccount.getBalance().add(feesBd));
                    accountRepository.save(feesAccount);

                    Transaction feesTransaction = new Transaction();
                    feesTransaction.setAccountId(feesAccount.getId());
                    feesTransaction.setAmount(feesBd);
                    feesTransaction.setType("CREDIT_FEES");
                    feesTransaction.setStatus(TransactionStatus.COMPLETED);
                    feesTransaction.setReference(commonReference);
                    feesTransaction.setDescription(String.format(
                            "Frais virement interne (10%%) - %d FCFA sur envoi de %d FCFA",
                            fees.longValue(),
                            amount.longValue()
                    ));
                    feesTransaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(feesTransaction);
                }
            }

            // LOG DE SUCCÈS
            loggingService.logTransaction(
                    "INTERNAL_TRANSFER_FROM_PHONE", "VIREMENT_INTERNE", amount,
                    sourceAccount.getAccountNumber(), request.getTargetAccountNumber(),
                    request.getSourcePhone(), null,
                    request.getDescription(), transactionRef,
                    "SUCCESS", null, fees, totalAmount, httpServletRequest
            );

            //  Réponse claire
            response.put("success", true);
            response.put("message", "Virement interne effectué avec succès");
            response.put("amountSent", request.getAmount()); // Montant reçu par le destinataire
            response.put("fees", fees.intValue());
            response.put("totalDebited", totalAmount.intValue()); // Total débité
            response.put("sourcePhone", request.getSourcePhone());
            response.put("sourceAccount", sourceAccount.getAccountNumber());
            response.put("targetAccount", request.getTargetAccountNumber());

            //  Détails pour l'utilisateur
            response.put("details", String.format(
                    " %d FCFA envoyés à %s. Frais: %d FCFA. Total débité: %d FCFA.",
                    amount.longValue(),
                    request.getTargetAccountNumber(),
                    fees.longValue(),
                    totalAmount.longValue()
            ));

            return ResponseEntity.ok(maskSensitiveInfo(response));

        } catch (Exception e) {
            loggingService.logTransaction(
                    "INTERNAL_TRANSFER_FROM_PHONE", "VIREMENT_INTERNE", amount,
                    request.getSourceAccountNumber(), request.getTargetAccountNumber(),
                    request.getSourcePhone(), null,
                    request.getDescription(), transactionRef,
                    "FAILED", e.getMessage(), fees, totalAmount, httpServletRequest
            );
            log.error("Erreur lors du virement interne depuis téléphone", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
        }
    }


    // 3. TRANSFERT MOBILE MONEY (COMPTE → Téléphone) - 10% frais


    @PostMapping("/mobile-money/from-account")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> transferFromAccountToMobileMoney(@RequestBody MobileMoneyTransferRequest request) {
        log.info("Transfert compte vers Mobile Money - Account: {}, Amount: {} FCFA, Recipient: {}",
                request.getAccountNumber(), request.getAmount(), request.getRecipientPhone());

        Map<String, Object> response = new HashMap<>();
        Double amountToSend = request.getAmount(); //  Montant que le destinataire doit recevoir

        //  Calcul des frais (10% du montant envoyé)
        Double fees = Math.ceil(amountToSend * 0.1);
        Double totalAmount = amountToSend + fees;
        String transactionRef = UUID.randomUUID().toString();

        try {
            BigDecimal amountToSendBd = new BigDecimal(amountToSend);
            BigDecimal feesBd = new BigDecimal(fees);
            BigDecimal totalAmountBd = new BigDecimal(totalAmount);

            if (amountToSendBd.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

            if (amountToSendBd.compareTo(BigDecimal.ZERO) <= 0) {
                response.put("success", false);
                response.put("message", "Le montant doit être supérieur à 0");
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

            Account sourceAccount = accountRepository.findByAccountNumber(request.getAccountNumber())
                    .orElseThrow(() -> new RuntimeException("Compte source non trouvé: " + request.getAccountNumber()));

            //  Vérifier le solde (montant + frais)
            if (sourceAccount.getBalance().compareTo(totalAmountBd) < 0) {
                response.put("success", false);
                response.put("message", String.format(
                        "Solde insuffisant. Solde: %d FCFA, Montant à envoyer: %d FCFA, Frais: %d FCFA, Total requis: %d FCFA",
                        sourceAccount.getBalance().longValue(),
                        amountToSend.longValue(),
                        fees.longValue(),
                        totalAmount.longValue()
                ));
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

            Client sourceClient = clientRepository.findById(sourceAccount.getClientId())
                    .orElseThrow(() -> new RuntimeException("Client source non trouvé"));

            //  Appeler le service avec le bon montant
            boolean success = mobileMoneyService.transferToMobileMoney(
                    sourceClient.getPhoneNumber(),
                    amountToSendBd //  Le destinataire reçoit exactement ce montant
            );

            if (success) {
                //  Débiter le compte (montant envoyé + frais)
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmountBd));
                accountRepository.save(sourceAccount);

                String commonReference = UUID.randomUUID().toString();

                //  Transaction de débit (montant total)
                Transaction debitTransaction = new Transaction();
                debitTransaction.setAccountId(sourceAccount.getId());
                debitTransaction.setAmount(totalAmountBd); //  Total débité
                debitTransaction.setType("DEBIT_MOBILE_MONEY");
                debitTransaction.setStatus(TransactionStatus.COMPLETED);
                debitTransaction.setReference(commonReference);
                debitTransaction.setDescription(String.format(
                        "Transfert Mobile Money de %d FCFA (dont frais: %d FCFA) vers %s",
                        amountToSend.longValue(),
                        fees.longValue(),
                        request.getRecipientPhone()
                ));
                debitTransaction.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(debitTransaction);

                //  Transaction de frais
                Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
                if (feesAccount != null && feesBd.compareTo(BigDecimal.ZERO) > 0) {
                    feesAccount.setBalance(feesAccount.getBalance().add(feesBd));
                    accountRepository.save(feesAccount);

                    Transaction feesTransaction = new Transaction();
                    feesTransaction.setAccountId(feesAccount.getId());
                    feesTransaction.setAmount(feesBd);
                    feesTransaction.setType("CREDIT_FEES");
                    feesTransaction.setStatus(TransactionStatus.COMPLETED);
                    feesTransaction.setReference(commonReference);
                    feesTransaction.setDescription(String.format(
                            "Frais Mobile Money (10%%) - %d FCFA sur envoi de %d FCFA",
                            fees.longValue(),
                            amountToSend.longValue()
                    ));
                    feesTransaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(feesTransaction);
                }

                // LOG DE SUCCÈS
                loggingService.logTransaction(
                        "MOBILE_MONEY_FROM_ACCOUNT", "DEBIT_MOBILE_MONEY",
                        amountToSend,
                        request.getAccountNumber(),
                        null,
                        null,
                        request.getRecipientPhone(),
                        request.getDescription(),
                        transactionRef,
                        "SUCCESS",
                        null,
                        fees,
                        totalAmount,
                        httpServletRequest
                );

                //  Réponse claire
                response.put("success", true);
                response.put("message", "Transfert Mobile Money effectué avec succès");
                response.put("amountSent", amountToSend); // Montant reçu par le destinataire
                response.put("fees", fees.intValue());
                response.put("totalDebited", totalAmount.intValue()); // Total débité
                response.put("recipientPhone", request.getRecipientPhone());
                response.put("sourceAccount", request.getAccountNumber());

                //  Détails pour l'utilisateur
                response.put("details", String.format(
                        " %d FCFA envoyés à %s. Frais: %d FCFA. Total débité: %d FCFA.",
                        amountToSend.longValue(),
                        request.getRecipientPhone(),
                        fees.longValue(),
                        totalAmount.longValue()
                ));

                return ResponseEntity.ok(maskSensitiveInfo(response));

            } else {
                loggingService.logTransaction(
                        "MOBILE_MONEY_FROM_ACCOUNT", "DEBIT_MOBILE_MONEY",
                        amountToSend,
                        request.getAccountNumber(),
                        null,
                        null,
                        request.getRecipientPhone(),
                        request.getDescription(),
                        transactionRef,
                        "FAILED",
                        "Échec du transfert Mobile Money",
                        fees,
                        totalAmount,
                        httpServletRequest
                );
                response.put("success", false);
                response.put("message", "Échec du transfert Mobile Money");
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

        } catch (Exception e) {
            loggingService.logTransaction(
                    "MOBILE_MONEY_FROM_ACCOUNT", "DEBIT_MOBILE_MONEY",
                    amountToSend,
                    request.getAccountNumber(),
                    null,
                    null,
                    request.getRecipientPhone(),
                    request.getDescription(),
                    transactionRef,
                    "FAILED",
                    e.getMessage(),
                    fees,
                    totalAmount,
                    httpServletRequest
            );
            log.error("Erreur lors du transfert Mobile Money", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
        }
    }


    // 4. TRANSFERT MOBILE MONEY (Téléphone Orabank → Téléphone) - 10% frais


    @PostMapping("/mobile-money/from-phone")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> transferFromPhoneToMobileMoney(@RequestBody MobileMoneyFromPhoneRequest request) {
        log.info("Transfert téléphone vers Mobile Money - Source: {}, Amount: {} FCFA, Recipient: {}",
                request.getSourcePhone(), request.getAmount(), request.getRecipientPhone());

        Map<String, Object> response = new HashMap<>();
        Double amountToSend = request.getAmount(); //  Montant que le destinataire doit recevoir

        //  Calcul des frais (10% du montant envoyé)
        Double fees = Math.ceil(amountToSend * 0.1);
        Double totalAmount = amountToSend + fees;
        String transactionRef = UUID.randomUUID().toString();

        try {
            BigDecimal amountToSendBd = new BigDecimal(amountToSend);
            BigDecimal feesBd = new BigDecimal(fees);
            BigDecimal totalAmountBd = new BigDecimal(totalAmount);

            if (amountToSendBd.compareTo(MAX_AMOUNT) > 0) {
                response.put("success", false);
                response.put("message", "Le montant maximum autorisé est de 500 000 FCFA");
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

            if (amountToSendBd.compareTo(BigDecimal.ZERO) <= 0) {
                response.put("success", false);
                response.put("message", "Le montant doit être supérieur à 0");
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

            Client sourceClient = clientRepository.findByPhoneNumber(request.getSourcePhone())
                    .orElseThrow(() -> new RuntimeException("Client Orabank non trouvé: " + request.getSourcePhone()));

            //  Récupérer le compte du client (gestion multi-comptes)
            List<Account> clientAccounts = accountRepository.findByClientIdAndActiveTrue(sourceClient.getId());
            if (clientAccounts.isEmpty()) {
                throw new RuntimeException("Aucun compte actif trouvé pour ce client");
            }
            Account sourceAccount = clientAccounts.get(0);
            log.info("Compte source utilisé: {} (parmi {} comptes disponibles)",
                    sourceAccount.getAccountNumber(), clientAccounts.size());

            //  Vérifier le solde (montant + frais)
            if (sourceAccount.getBalance().compareTo(totalAmountBd) < 0) {
                response.put("success", false);
                response.put("message", String.format(
                        "Solde insuffisant. Solde: %d FCFA, Montant à envoyer: %d FCFA, Frais: %d FCFA, Total requis: %d FCFA",
                        sourceAccount.getBalance().longValue(),
                        amountToSend.longValue(),
                        fees.longValue(),
                        totalAmount.longValue()
                ));
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

            //  Appeler le service avec le bon montant
            boolean success = mobileMoneyService.transferToMobileMoney(
                    sourceClient.getPhoneNumber(),
                    amountToSendBd //  Le destinataire reçoit exactement ce montant
            );

            if (success) {
                // Débiter le compte (montant envoyé + frais)
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalAmountBd));
                accountRepository.save(sourceAccount);

                String commonReference = UUID.randomUUID().toString();

                //  Transaction de débit (montant total)
                Transaction debitTransaction = new Transaction();
                debitTransaction.setAccountId(sourceAccount.getId());
                debitTransaction.setAmount(totalAmountBd); //  Total débité
                debitTransaction.setType("DEBIT_MOBILE_MONEY");
                debitTransaction.setStatus(TransactionStatus.COMPLETED);
                debitTransaction.setReference(commonReference);
                debitTransaction.setDescription(String.format(
                        "Transfert Mobile Money de %d FCFA (dont frais: %d FCFA) de %s vers %s",
                        amountToSend.longValue(),
                        fees.longValue(),
                        request.getSourcePhone(),
                        request.getRecipientPhone()
                ));
                debitTransaction.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(debitTransaction);

                //  Transaction de frais
                Account feesAccount = accountRepository.findByAccountNumber("FEE_MOBILE_MONEY_001").orElse(null);
                if (feesAccount != null && feesBd.compareTo(BigDecimal.ZERO) > 0) {
                    feesAccount.setBalance(feesAccount.getBalance().add(feesBd));
                    accountRepository.save(feesAccount);

                    Transaction feesTransaction = new Transaction();
                    feesTransaction.setAccountId(feesAccount.getId());
                    feesTransaction.setAmount(feesBd);
                    feesTransaction.setType("CREDIT_FEES");
                    feesTransaction.setStatus(TransactionStatus.COMPLETED);
                    feesTransaction.setReference(commonReference);
                    feesTransaction.setDescription(String.format(
                            "Frais Mobile Money (10%%) - %d FCFA sur envoi de %d FCFA",
                            fees.longValue(),
                            amountToSend.longValue()
                    ));
                    feesTransaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(feesTransaction);
                }

                // LOG DE SUCCÈS
                loggingService.logTransaction(
                        "MOBILE_MONEY_FROM_PHONE", "DEBIT_MOBILE_MONEY",
                        amountToSend,
                        sourceAccount.getAccountNumber(),
                        null,
                        request.getSourcePhone(),
                        request.getRecipientPhone(),
                        request.getDescription(),
                        transactionRef,
                        "SUCCESS",
                        null,
                        fees,
                        totalAmount,
                        httpServletRequest
                );

                //  Réponse claire
                response.put("success", true);
                response.put("message", "Transfert Mobile Money effectué avec succès");
                response.put("amountSent", amountToSend); // Montant reçu par le destinataire
                response.put("fees", fees.intValue());
                response.put("totalDebited", totalAmount.intValue()); // Total débité
                response.put("sourcePhone", request.getSourcePhone());
                response.put("sourceAccount", sourceAccount.getAccountNumber());
                response.put("recipientPhone", request.getRecipientPhone());

                //  Détails pour l'utilisateur
                response.put("details", String.format(
                        "%d FCFA envoyés à %s. Frais: %d FCFA. Total débité: %d FCFA.",
                        amountToSend.longValue(),
                        request.getRecipientPhone(),
                        fees.longValue(),
                        totalAmount.longValue()
                ));

                return ResponseEntity.ok(maskSensitiveInfo(response));

            } else {
                loggingService.logTransaction(
                        "MOBILE_MONEY_FROM_PHONE", "DEBIT_MOBILE_MONEY",
                        amountToSend,
                        sourceAccount.getAccountNumber(),
                        null,
                        request.getSourcePhone(),
                        request.getRecipientPhone(),
                        request.getDescription(),
                        transactionRef,
                        "FAILED",
                        "Échec du transfert Mobile Money",
                        fees,
                        totalAmount,
                        httpServletRequest
                );
                response.put("success", false);
                response.put("message", "Échec du transfert Mobile Money");
                return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
            }

        } catch (Exception e) {
            loggingService.logTransaction(
                    "MOBILE_MONEY_FROM_PHONE", "DEBIT_MOBILE_MONEY",
                    amountToSend,
                    null,
                    null,
                    request.getSourcePhone(),
                    request.getRecipientPhone(),
                    request.getDescription(),
                    transactionRef,
                    "FAILED",
                    e.getMessage(),
                    fees,
                    totalAmount,
                    httpServletRequest
            );
            log.error("Erreur lors du transfert Mobile Money", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(maskSensitiveInfo(response));
        }
    }


    // DTOs


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
        private String sourceAccountNumber;
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
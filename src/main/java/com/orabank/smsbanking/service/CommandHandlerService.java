package com.orabank.smsbanking.service;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.security.PhoneVerificationService;
import com.orabank.smsbanking.util.DateFormatter;
import com.orabank.smsbanking.util.LoggingUtil;
import com.orabank.smsbanking.util.SmsParser;
import com.orabank.smsbanking.util.SmsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommandHandlerService {

    private final AccountService accountService;
    private final PhoneVerificationService phoneVerificationService;
    private final MobileMoneyService mobileMoneyService;
    private final SmsParser smsParser;

    @Value("${app.sms.prefix:ORABANK}")
    private String smsPrefix;

    // ============================================================
    // PATTERNS POUR LE PARSING DES COMMANDES
    // ============================================================

    private static final Pattern BALANCE_PATTERN = Pattern.compile("(?i)^SOLDE\\?\\s*(\\w+)?$");
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "(?i)^TRANSFERT\\s+(\\d+)\\s*(\\w+)?\\s*(MOBILE)?$"
    );
    private static final Pattern HISTORY_PATTERN = Pattern.compile("(?i)^HISTO\\s*(\\w+)?$");

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    private String normalizePhoneNumber(String phoneNumber) {
        log.info("Normalizing phone number: {}", phoneNumber);
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);
        log.info("Normalized result: {}", normalized);
        return normalized;
    }

    // ============================================================
    // MÉTHODE PRINCIPALE DE TRAITEMENT DES COMMANDES
    // ============================================================

    public String handleCommand(String command, String phoneNumber, String rawMessage) {
        log.info("=== HANDLE COMMAND START ===");
        log.info("Command: {}, Phone: {}, RawMessage: {}", command, LoggingUtil.maskPhoneNumber(phoneNumber), rawMessage);

        String result = switch (command.toUpperCase()) {
            case "SOLDE" -> handleBalance(phoneNumber, rawMessage);
            case "HISTO" -> handleHistory(phoneNumber, rawMessage);
            case "OTP" -> handleOtp(phoneNumber);
            case "TRANSFER" -> handleTransfer(phoneNumber, rawMessage);
            case "HELP" -> handleHelp();
            default -> handleUnknownCommand(command);
        };

        log.info("=== HANDLE COMMAND RESULT: {}", result);
        return result;
    }

    // ============================================================
    // COMMANDE: SOLDE
    // ============================================================

    private String handleBalance(String phoneNumber, String rawMessage) {
        try {
            log.info("=== HANDLE BALANCE START ===");
            log.info("phoneNumber: {}, rawMessage: {}", phoneNumber, rawMessage);

            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            log.info("normalizedPhone: {}", normalizedPhone);

            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }

            Matcher matcher = BALANCE_PATTERN.matcher(rawMessage.trim());
            String accountNumber = null;
            if (matcher.matches()) {
                accountNumber = matcher.group(1);
            }
            log.info("accountNumber extracted: {}", accountNumber);

            log.info("Calling accountService.getAccountsByPhone({})", normalizedPhone);
            List<Account> accounts = accountService.getAccountsByPhone(normalizedPhone);
            log.info("Accounts found: {}", accounts.size());

            if (accounts.isEmpty()) {
                log.warn("No accounts found for phone: {}", normalizedPhone);
                return String.format("%s - Aucun compte trouvé pour ce client.", smsPrefix);
            }

            // Si un numéro de compte est spécifié
            if (accountNumber != null && !accountNumber.isEmpty()) {
                log.info("Looking for specific account: {}", accountNumber);
                final String finalAccountNumber = accountNumber;

                Optional<Account> optionalAccount = accounts.stream()
                        .filter(a -> a.getAccountNumber().equalsIgnoreCase(finalAccountNumber))
                        .findFirst();

                if (optionalAccount.isEmpty()) {
                    String accountList = accounts.stream()
                            .map(Account::getAccountNumber)
                            .collect(Collectors.joining(", "));
                    log.warn("Account {} not found. Available: {}", accountNumber, accountList);
                    return String.format("%s - Compte %s introuvable. Vos comptes: %s",
                            smsPrefix, accountNumber, accountList);
                }

                Account account = optionalAccount.get();
                log.info("Balance found: {} for account {}", account.getBalance(), account.getAccountNumber());
                return String.format("%s - Votre solde est de: %d FCFA (%s)",
                        smsPrefix,
                        account.getBalance().longValue(),
                        account.getAccountNumber());
            }

            // Pas de numéro de compte spécifié
            if (accounts.size() == 1) {
                log.info("Single account found, returning balance");
                return String.format("%s - Votre solde est de: %d FCFA",
                        smsPrefix, accounts.get(0).getBalance().longValue());
            }

            // Plusieurs comptes - retourner la liste
            log.info("Multiple accounts found ({})", accounts.size());
            StringBuilder sb = new StringBuilder();
            sb.append(smsPrefix).append(" - Vos comptes:\n");
            for (Account account : accounts) {
                sb.append(String.format("%s: %d FCFA\n",
                        account.getAccountNumber(),
                        account.getBalance().longValue()));
            }
            sb.append("Exemple: SOLDE? COMPTEXXX");
            return sb.toString().trim();

        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé: {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Client non trouvé. Veuillez contacter votre agence.", smsPrefix);
        } catch (Exception e) {
            log.error("ERREUR DANS HANDLE BALANCE: {}", e.getMessage(), e);
            return String.format("%s - Erreur technique. Veuillez réessayer.", smsPrefix);
        }
    }

    // ============================================================
    // COMMANDE: HISTO (Historique)
    // ============================================================

    private String handleHistory(String phoneNumber, String rawMessage) {
        try {
            log.info("=== HANDLE HISTORY START ===");

            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }

            Matcher matcher = HISTORY_PATTERN.matcher(rawMessage.trim());
            String accountNumber = null;
            if (matcher.matches()) {
                accountNumber = matcher.group(1);
            }

            List<Account> accounts = accountService.getAccountsByPhone(normalizedPhone);

            if (accounts.isEmpty()) {
                return String.format("%s - Aucun compte trouvé pour ce client.", smsPrefix);
            }

            Account selectedAccount;
            if (accountNumber != null && !accountNumber.isEmpty()) {
                final String finalAccountNumber = accountNumber;

                selectedAccount = accounts.stream()
                        .filter(a -> a.getAccountNumber().equalsIgnoreCase(finalAccountNumber))
                        .findFirst()
                        .orElse(null);

                if (selectedAccount == null) {
                    String accountList = accounts.stream()
                            .map(Account::getAccountNumber)
                            .collect(Collectors.joining(", "));
                    return String.format("%s - Compte %s introuvable. Vos comptes: %s",
                            smsPrefix, accountNumber, accountList);
                }
            } else {
                if (accounts.size() > 1) {
                    String accountList = accounts.stream()
                            .map(Account::getAccountNumber)
                            .collect(Collectors.joining(", "));
                    return String.format("%s - Plusieurs comptes trouvés. Veuillez spécifier: HISTO COMPTEXXX. Vos comptes: %s",
                            smsPrefix, accountList);
                }
                selectedAccount = accounts.get(0);
            }

            List<Transaction> transactions = accountService.getLastTransactions(
                    normalizedPhone, selectedAccount.getAccountNumber(), 5);

            if (transactions.isEmpty()) {
                return String.format("%s - Aucune transaction récente sur le compte %s.",
                        smsPrefix, selectedAccount.getAccountNumber());
            }

            StringBuilder sb = new StringBuilder();
            sb.append(smsPrefix).append(" - Dernieres transactions (").append(selectedAccount.getAccountNumber()).append("):\n");

            for (Transaction t : transactions) {
                sb.append(String.format("%s %s %d FCFA\n",
                        DateFormatter.formatStandard(t.getCreatedAt()),
                        t.getType(),
                        t.getAmount().longValue()));
            }

            return sb.toString().trim();

        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Client non trouvé.", smsPrefix);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'historique pour {}",
                    LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Service temporairement indisponible. Veuillez réessayer.", smsPrefix);
        }
    }

    // ============================================================
    // COMMANDE: OTP
    // ============================================================

    private String handleOtp(String phoneNumber) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            log.debug("Génération OTP pour le numéro normalisé: {}", normalizedPhone);

            if (normalizedPhone == null || normalizedPhone.isEmpty()) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }

            boolean sent = phoneVerificationService.generateAndSendOtp(normalizedPhone);

            if (!sent) {
                log.error("Échec d'envoi de l'OTP pour: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
                return String.format("%s - Erreur lors de l'envoi de l'OTP.", smsPrefix);
            }

            return String.format("%s - Code OTP envoyé par SMS. Valable 5 min.", smsPrefix);
        } catch (Exception e) {
            log.error("Erreur lors de la génération OTP pour {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Erreur lors de la génération OTP.", smsPrefix);
        }
    }

    // ============================================================
    // COMMANDE: TRANSFERT
    // ============================================================

    private String handleTransfer(String phoneNumber, String rawMessage) {
        try {
            log.info("=== HANDLE TRANSFER START ===");

            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }

            String trimmedMessage = rawMessage.trim();
            if (trimmedMessage.equalsIgnoreCase("TRANSFER")) {
                return String.format("%s - Format invalide. Exemple: TRANSFERT 1000 COMPTEXXX +22893360150", smsPrefix);
            }

            Matcher transferMatcher = TRANSFER_PATTERN.matcher(trimmedMessage);
            String accountNumber = null;
            boolean isMobileMoney = false;

            if (transferMatcher.matches()) {
                accountNumber = transferMatcher.group(2);
                isMobileMoney = "MOBILE".equalsIgnoreCase(transferMatcher.group(3));
            }

            Long amountLong = smsParser.extractTransferAmount(rawMessage);
            if (amountLong == null || amountLong == 0) {
                return String.format("%s - Montant invalide. Exemple: TRANSFERT 1000 COMPTEXXX +22893360150", smsPrefix);
            }

            if (amountLong < 0) {
                return String.format("%s - Le montant ne peut pas être négatif.", smsPrefix);
            }

            BigDecimal amount = BigDecimal.valueOf(amountLong);

            String recipientPhoneRaw = smsParser.extractRecipientPhone(rawMessage);
            if (recipientPhoneRaw == null) {
                return String.format("%s - Numéro du destinataire manquant. Exemple: TRANSFERT 1000 COMPTEXXX +22893360150", smsPrefix);
            }

            String recipientPhone = normalizePhoneNumber(recipientPhoneRaw);
            if (recipientPhone == null) {
                return String.format("%s - Numéro du destinataire invalide. Format attendu: +228XXXXXXXX", smsPrefix);
            }

            if (normalizedPhone.equals(recipientPhone)) {
                return String.format("%s - Impossible de virer de l'argent vers votre propre compte.", smsPrefix);
            }

            List<Account> accounts = accountService.getAccountsByPhone(normalizedPhone);
            if (accounts.isEmpty()) {
                return String.format("%s - Aucun compte trouvé pour ce client.", smsPrefix);
            }

            Account sourceAccount;
            if (accountNumber != null && !accountNumber.isEmpty()) {
                final String finalAccountNumber = accountNumber;

                sourceAccount = accounts.stream()
                        .filter(a -> a.getAccountNumber().equalsIgnoreCase(finalAccountNumber))
                        .findFirst()
                        .orElse(null);

                if (sourceAccount == null) {
                    String accountList = accounts.stream()
                            .map(Account::getAccountNumber)
                            .collect(Collectors.joining(", "));
                    return String.format("%s - Compte %s introuvable. Vos comptes: %s",
                            smsPrefix, accountNumber, accountList);
                }
            } else {
                if (accounts.size() > 1) {
                    String accountList = accounts.stream()
                            .map(Account::getAccountNumber)
                            .collect(Collectors.joining(", "));
                    return String.format("%s - Plusieurs comptes trouvés. Veuillez spécifier le compte source: TRANSFERT %d COMPTEXXX +228... (Vos comptes: %s)",
                            smsPrefix, amount.longValue(), accountList);
                }
                sourceAccount = accounts.get(0);
            }

            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                log.warn("Solde insuffisant - Compte: {}, Solde: {}, Montant requis: {}",
                        sourceAccount.getAccountNumber(), sourceAccount.getBalance(), amount);
                return String.format("%s - Solde insuffisant sur le compte %s. Solde actuel: %d FCFA.",
                        smsPrefix, sourceAccount.getAccountNumber(), sourceAccount.getBalance().longValue());
            }

            if (isMobileMoney) {
                if (amount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
                    return String.format("%s - Montant maximum pour Mobile Money: 1 000 000 FCFA", smsPrefix);
                }

                boolean success = mobileMoneyService.transferToMobileMoney(normalizedPhone, amount);

                if (success) {
                    log.info("Virement Mobile Money réussi - Émetteur: {}, Compte: {}, Montant: {} FCFA",
                            LoggingUtil.maskPhoneNumber(normalizedPhone),
                            sourceAccount.getAccountNumber(), amount);
                    return String.format("%s - Virement %d FCFA vers Mobile Money effectué depuis %s.",
                            smsPrefix, amount.longValue(), sourceAccount.getAccountNumber());
                } else {
                    log.warn("Virement Mobile Money échoué - Émetteur: {}, Compte: {}, Montant: {} FCFA",
                            LoggingUtil.maskPhoneNumber(normalizedPhone),
                            sourceAccount.getAccountNumber(), amount);
                    return String.format("%s - Échec du virement vers Mobile Money depuis %s. Vérifiez votre solde.",
                            smsPrefix, sourceAccount.getAccountNumber());
                }
            } else {
                accountService.transferFromAccount(sourceAccount, recipientPhone, amount, "Virement interne SMS");

                log.info("Virement interne réussi - Émetteur: {}, Compte: {}, Bénéficiaire: {}, Montant: {} FCFA",
                        LoggingUtil.maskPhoneNumber(normalizedPhone),
                        sourceAccount.getAccountNumber(),
                        LoggingUtil.maskPhoneNumber(recipientPhone),
                        amount);

                return String.format("%s - Virement de %d FCFA vers %s effectué depuis %s.",
                        smsPrefix,
                        amount.longValue(),
                        LoggingUtil.maskPhoneNumber(recipientPhone),
                        sourceAccount.getAccountNumber());
            }

        } catch (InsufficientBalanceException e) {
            log.warn("Solde insuffisant capturé - Émetteur: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Solde insuffisant. Votre solde actuel ne permet pas ce virement.", smsPrefix);

        } catch (NumberFormatException e) {
            return String.format("%s - Montant invalide. Exemple: TRANSFERT 1000 COMPTEXXX +22893360150", smsPrefix);

        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé lors du virement - Émetteur: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Client non trouvé. Vérifiez votre numéro de téléphone.", smsPrefix);

        } catch (Exception e) {
            log.error("Erreur inattendue lors du virement pour {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Erreur interne. Veuillez réessayer.", smsPrefix);
        }
    }

    // ============================================================
    // COMMANDE: HELP
    // ============================================================

    private String handleHelp() {
        return smsPrefix + " - Commandes disponibles:\n" +
                "SOLDE? - Consulter solde\n" +
                "SOLDE? COMPTEXXX - Consulter solde d'un compte spécifique\n" +
                "HISTO - 5 dernieres transactions\n" +
                "HISTO COMPTEXXX - Historique d'un compte spécifique\n" +
                "OTP - Generer code OTP\n" +
                "TRANSFERT X - Virement (ex: TRANSFERT 1000 COMPTE002 +22893360150)\n" +
                "TRANSFERT X COMPTEXXX - Virement depuis un compte spécifique\n" +
                "TRANSFERT X MOBILE - Virement Mobile Money\n" +
                "HELP - Cette aide";
    }

    // ============================================================
    // GESTION DES COMMANDES INCONNUES
    // ============================================================

    private String handleUnknownCommand(String command) {
        log.warn("Commande inconnue: {}", command);
        return String.format("%s - Commande inconnue. Tapez HELP pour la liste des commandes.",
                smsPrefix);
    }
}
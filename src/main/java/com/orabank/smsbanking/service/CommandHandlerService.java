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

    
    // PATTERNS POUR LE PARSING DES COMMANDES
    

    private static final Pattern BALANCE_PATTERN = Pattern.compile("(?i)^SOLDE\\?\\s*(\\w+)?$");
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "(?i)^TRANSFERT\\s+(\\d+)\\s*(\\w+)?\\s*(MOBILE)?$"
    );
    private static final Pattern HISTORY_PATTERN = Pattern.compile("(?i)^HISTO\\s*(\\w+)?$");

    
    // MÉTHODES UTILITAIRES
    

    private String normalizePhoneNumber(String phoneNumber) {
        log.info("Normalizing phone number: {}", phoneNumber);
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);
        log.info("Normalized result: {}", normalized);
        return normalized;
    }

    
    // MÉTHODE PRINCIPALE DE TRAITEMENT DES COMMANDES
    

    public String handleCommand(String command, String phoneNumber, String rawMessage) {
        log.info("=== HANDLE COMMAND START ===");
        log.info("Command: {}, Phone: {}, RawMessage: {}", command, LoggingUtil.maskPhoneNumber(phoneNumber), rawMessage);

        // Normaliser la commande pour enlever le ? si présent
        String normalizedCommand = command.toUpperCase().replace("?", "");
        log.info("Normalized command: {}", normalizedCommand);

        String result = switch (normalizedCommand) {
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

    
    // COMMANDE: SOLDE
    

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
                return String.format("%s - Aucun compte trouvé.", smsPrefix);
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

                return String.format("%s: %d FCFA",
                        account.getAccountNumber(),
                        account.getBalance().longValue());
            }

            // Format simplifié pour la liste des comptes
            StringBuilder sb = new StringBuilder();
            for (Account account : accounts) {
                sb.append(String.format("%s: %d FCFA\n",
                        account.getAccountNumber(),
                        account.getBalance().longValue()));
            }
            return sb.toString().trim();

        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé: {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Client non trouvé.", smsPrefix);
        } catch (Exception e) {
            log.error("ERREUR DANS HANDLE BALANCE: {}", e.getMessage(), e);
            return String.format("%s - Erreur technique. Veuillez réessayer.", smsPrefix);
        }
    }

    
    // COMMANDE: HISTO (Historique)
    

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
                return String.format("%s - Aucun compte trouvé.", smsPrefix);
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
                return String.format("%s - Aucune transaction récente sur %s.",
                        smsPrefix, selectedAccount.getAccountNumber());
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s - Historique %s:\n",
                    smsPrefix, selectedAccount.getAccountNumber()));

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

    
    // COMMANDE: OTP
    

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

    
    // COMMANDE: TRANSFERT
    

    private String handleTransfer(String phoneNumber, String rawMessage) {
        try {
            log.info("=== HANDLE TRANSFER START ===");

            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }

            String trimmedMessage = rawMessage.trim();
            if (trimmedMessage.equalsIgnoreCase("TRANSFER")) {
                return String.format("%s - Format invalide. Exemple: TRANSFERT 50000 COMPTE002 +22890000003 COMPTE003", smsPrefix);
            }

            Matcher transferMatcher = TRANSFER_PATTERN.matcher(trimmedMessage);
            String sourceAccountNumber = null;
            boolean isMobileMoney = false;

            if (transferMatcher.matches()) {
                sourceAccountNumber = transferMatcher.group(2);
                isMobileMoney = "MOBILE".equalsIgnoreCase(transferMatcher.group(3));
            }

            Long amountLong = smsParser.extractTransferAmount(rawMessage);
            if (amountLong == null || amountLong == 0) {
                return String.format("%s - Montant invalide. Exemple: TRANSFERT 50000 COMPTE002 +22890000003 COMPTE003", smsPrefix);
            }

            if (amountLong < 0) {
                return String.format("%s - Le montant ne peut pas être négatif.", smsPrefix);
            }

            BigDecimal amount = BigDecimal.valueOf(amountLong);

            String recipientPhoneRaw = smsParser.extractRecipientPhone(rawMessage);
            if (recipientPhoneRaw == null) {
                return String.format("%s - Numéro du destinataire manquant. Exemple: TRANSFERT 50000 COMPTE002 +22890000003 COMPTE003", smsPrefix);
            }

            String recipientPhone = normalizePhoneNumber(recipientPhoneRaw);
            if (recipientPhone == null) {
                return String.format("%s - Numéro du destinataire invalide. Format attendu: +228XXXXXXXX", smsPrefix);
            }

            //  EXTRAIRE LE COMPTE DESTINATAIRE (après le numéro de téléphone)
            String recipientAccountNumber = smsParser.extractTargetAccountNumber(rawMessage);
            log.info("Compte destinataire extrait: {}", recipientAccountNumber);

            if (normalizedPhone.equals(recipientPhone)) {
                return String.format("%s - Impossible de virer de l'argent vers votre propre compte.", smsPrefix);
            }

            //  Récupérer les comptes du client émetteur
            List<Account> accounts = accountService.getAccountsByPhone(normalizedPhone);
            if (accounts.isEmpty()) {
                return String.format("%s - Aucun compte trouvé.", smsPrefix);
            }

            //  Sélectionner le compte source
            Account sourceAccount;
            if (sourceAccountNumber != null && !sourceAccountNumber.isEmpty()) {
                final String finalSourceAccountNumber = sourceAccountNumber;

                sourceAccount = accounts.stream()
                        .filter(a -> a.getAccountNumber().equalsIgnoreCase(finalSourceAccountNumber))
                        .findFirst()
                        .orElse(null);

                if (sourceAccount == null) {
                    String accountList = accounts.stream()
                            .map(Account::getAccountNumber)
                            .collect(Collectors.joining(", "));
                    return String.format("%s - Compte %s introuvable. Vos comptes: %s",
                            smsPrefix, sourceAccountNumber, accountList);
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

            //  Vérifier le solde
            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                log.warn("Solde insuffisant - Compte: {}, Solde: {}, Montant requis: {}",
                        sourceAccount.getAccountNumber(), sourceAccount.getBalance(), amount);
                return String.format("%s - Solde insuffisant sur %s. Solde: %d FCFA.",
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
                    return String.format("%s - Échec du virement vers Mobile Money depuis %s.",
                            smsPrefix, sourceAccount.getAccountNumber());
                }
            } else {
                //  APPEL DE LA NOUVELLE MÉTHODE AVEC VÉRIFICATION DU COMPTE DESTINATAIRE
                accountService.transferFromAccountWithTargetAccount(
                        sourceAccount,
                        recipientPhone,
                        recipientAccountNumber,
                        amount,
                        "Virement interne SMS"
                );

                log.info("Virement interne réussi - Émetteur: {}, Compte source: {}, Bénéficiaire: {}, Compte destinataire: {}, Montant: {} FCFA",
                        LoggingUtil.maskPhoneNumber(normalizedPhone),
                        sourceAccount.getAccountNumber(),
                        LoggingUtil.maskPhoneNumber(recipientPhone),
                        recipientAccountNumber != null ? recipientAccountNumber : "AUTO",
                        amount);

                return String.format("%s - Virement %d FCFA vers %s effectué depuis %s.",
                        smsPrefix,
                        amount.longValue(),
                        LoggingUtil.maskPhoneNumber(recipientPhone),
                        sourceAccount.getAccountNumber());
            }

        } catch (InsufficientBalanceException e) {
            log.warn("Solde insuffisant capturé - Émetteur: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Solde insuffisant.", smsPrefix);

        } catch (NumberFormatException e) {
            return String.format("%s - Montant invalide. Exemple: TRANSFERT 50000 COMPTE002 +22890000003 COMPTE003", smsPrefix);

        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé lors du virement - Émetteur: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - %s", smsPrefix, e.getMessage());

        } catch (IllegalArgumentException e) {
            log.warn("Erreur de validation: {}", e.getMessage());
            return String.format("%s - %s", smsPrefix, e.getMessage());

        } catch (Exception e) {
            log.error("Erreur inattendue lors du virement pour {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Erreur interne. Veuillez réessayer.", smsPrefix);
        }
    }

    
    // COMMANDE: HELP
    

    private String handleHelp() {
        return smsPrefix + " - Commandes disponibles:\n" +
                "SOLDE? - Consulter solde\n" +
                "SOLDE? COMPTEXXX - Solde d'un compte spécifique\n" +
                "HISTO COMPTEXXX - Historique d'un compte\n" +
                "OTP - Generer code OTP\n" +
                "TRANSFERT X COMPTEXXX +228... COMPTEYYY - Virement avec compte source et destinataire\n" +
                "TRANSFERT X +228... COMPTEYYY - Virement avec compte destinataire\n" +
                "TRANSFERT X COMPTEXXX +228... - Virement avec compte source\n" +
                "TRANSFERT X +228... - Virement simple\n" +
                "TRANSFERT X MOBILE - Virement Mobile Money\n" +
                "HELP - Cette aide";
    }

    
    // GESTION DES COMMANDES INCONNUES
    

    private String handleUnknownCommand(String command) {
        log.warn("Commande inconnue: {}", command);
        return String.format("%s - Commande inconnue. Tapez HELP.", smsPrefix);
    }
}
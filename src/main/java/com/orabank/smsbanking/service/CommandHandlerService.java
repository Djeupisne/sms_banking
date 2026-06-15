package com.orabank.smsbanking.service;

import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.security.OtpGenerator;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class CommandHandlerService {

    private final AccountService accountService;
    private final OtpGenerator otpGenerator;
    private final MobileMoneyService mobileMoneyService;
    private final SmsParser smsParser;
    private final PhoneVerificationService phoneVerificationService;

    @Value("${app.sms.prefix:ORABANK}")
    private String smsPrefix;

    /**
     * Normalise le numéro de téléphone au format E.164 (+228XXXXXXXXX).
     *
     * @param phoneNumber le numéro brut
     * @return le numéro normalisé ou null si invalide
     */
    private String normalizePhoneNumber(String phoneNumber) {
        return SmsUtils.normalizePhoneNumber(phoneNumber);
    }

    public String handleCommand(String command, String phoneNumber, String rawMessage) {
        log.info("Traitement commande: {} pour {}", command, LoggingUtil.maskPhoneNumber(phoneNumber));

        return switch (command.toUpperCase()) {
            case "SOLDE" -> handleBalance(phoneNumber);
            case "HISTO" -> handleHistory(phoneNumber);
            case "OTP" -> handleOtp(phoneNumber);
            case "TRANSFER" -> handleTransfer(phoneNumber, rawMessage);
            case "HELP" -> handleHelp();
            default -> handleUnknownCommand(command);
        };
    }

    private String handleBalance(String phoneNumber) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }
            BigDecimal balance = accountService.getBalance(normalizedPhone);
            return String.format("%s - Votre solde est de: %d FCFA",
                    smsPrefix, balance.longValue());
        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Client non trouvé.", smsPrefix);
        } catch (Exception e) {
            log.error("Erreur lors de la consultation du solde pour {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Service temporairement indisponible. Veuillez réessayer.", smsPrefix);
        }
    }

    private String handleHistory(String phoneNumber) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }
            List<Transaction> transactions = accountService.getLastTransactions(normalizedPhone);

            if (transactions.isEmpty()) {
                return String.format("%s - Aucune transaction récente.", smsPrefix);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(smsPrefix).append(" - Dernieres transactions:\n");

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
            log.error("Erreur lors de la récupération de l'historique pour {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Service temporairement indisponible. Veuillez réessayer.", smsPrefix);
        }
    }

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

    private String handleTransfer(String phoneNumber, String rawMessage) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return String.format("%s - Numéro de téléphone invalide.", smsPrefix);
            }

            // Vérifier si le message est vide ou ne contient que TRANSFER
            String trimmedMessage = rawMessage.trim();
            if (trimmedMessage.equalsIgnoreCase("TRANSFER")) {
                return String.format("%s - Montant manquant. Exemple: TRANSFER 1000 +22893360150", smsPrefix);
            }

            // Extraire le montant
            Long amountLong = smsParser.extractTransferAmount(rawMessage);

            if (amountLong == null) {
                return String.format("%s - Montant invalide ou manquant. Exemple: TRANSFER 1000 +22893360150", smsPrefix);
            }

            if (amountLong == 0) {
                return String.format("%s - Le montant doit être supérieur à 0 FCFA. Exemple: TRANSFER 1000 +22893360150", smsPrefix);
            }

            if (amountLong < 0) {
                return String.format("%s - Le montant ne peut pas être négatif. Exemple: TRANSFER 1000 +22893360150", smsPrefix);
            }

            BigDecimal amount = BigDecimal.valueOf(amountLong);

            // Extraire le numéro du destinataire
            String recipientPhoneRaw = smsParser.extractRecipientPhone(rawMessage);
            if (recipientPhoneRaw == null) {
                return String.format("%s - Numéro du destinataire manquant. Exemple: TRANSFER 1000 +22893360150", smsPrefix);
            }

            String recipientPhone = normalizePhoneNumber(recipientPhoneRaw);
            if (recipientPhone == null) {
                return String.format("%s - Numéro du destinataire invalide. Format attendu: +228XXXXXXXX", smsPrefix);
            }

            //  Empêcher les transferts vers soi-même
            if (normalizedPhone.equals(recipientPhone)) {
                return String.format("%s - Impossible de virer de l'argent vers votre propre compte.", smsPrefix);
            }

            // Vérifier le solde AVANT toute opération
            BigDecimal balance = accountService.getBalance(normalizedPhone);
            if (balance.compareTo(amount) < 0) {
                log.warn("Solde insuffisant - Client: {}, Solde: {}, Montant requis: {}",
                        LoggingUtil.maskPhoneNumber(normalizedPhone), balance, amount);
                return String.format("%s - Solde insuffisant. Votre solde actuel est de %d FCFA.",
                        smsPrefix, balance.longValue());
            }

            boolean isMobileMoney = smsParser.isMobileTransfer(rawMessage);

            if (isMobileMoney) {
                // Vérification supplémentaire pour Mobile Money
                if (amount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
                    return String.format("%s - Montant maximum pour Mobile Money: 1 000 000 FCFA", smsPrefix);
                }

                boolean success = mobileMoneyService.transferToMobileMoney(normalizedPhone, amount);

                if (success) {
                    log.info("Virement Mobile Money réussi - Émetteur: {}, Montant: {} FCFA",
                            LoggingUtil.maskPhoneNumber(normalizedPhone), amount);
                    return String.format("%s - Virement %d FCFA vers Mobile Money effectué.",
                            smsPrefix, amount.longValue());
                } else {
                    log.warn("Virement Mobile Money échoué - Émetteur: {}, Montant: {} FCFA",
                            LoggingUtil.maskPhoneNumber(normalizedPhone), amount);
                    return String.format("%s - Échec du virement vers Mobile Money. Vérifiez votre solde.", smsPrefix);
                }
            } else {
                // Virement interne - Le solde a déjà été vérifié
                accountService.transfer(normalizedPhone, recipientPhone, amount, "Virement interne SMS");
                return String.format("%s - Virement de %d FCFA vers %s effectué.",
                        smsPrefix, amount.longValue(), LoggingUtil.maskPhoneNumber(recipientPhone));
            }

        } catch (InsufficientBalanceException e) {
            log.warn("Solde insuffisant capturé - Émetteur: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Solde insuffisant. Votre solde actuel ne permet pas ce virement.", smsPrefix);

        } catch (NumberFormatException e) {
            return String.format("%s - Montant invalide. Exemple: TRANSFER 1000 +22893360150", smsPrefix);

        } catch (ClientNotFoundException e) {
            log.warn("Client non trouvé lors du virement - Émetteur: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return String.format("%s - Client non trouvé. Vérifiez votre numéro de téléphone.", smsPrefix);

        } catch (Exception e) {
            log.error("Erreur inattendue lors du virement pour {}", LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return String.format("%s - Erreur interne. Veuillez réessayer.", smsPrefix);
        }
    }

    /**
     * Affiche l'aide avec les commandes disponibles.
     * Utilise des retours à laigne pour un affichage clair.
     */
    private String handleHelp() {
        return smsPrefix + " - Commandes disponibles:\n" +
                "SOLDE? - Consulter solde\n" +
                "HISTO - 5 dernieres transactions\n" +
                "OTP - Generer code OTP\n" +
                "TRANSFER X - Virement X FCFA (ex: TRANSFER 1000 +22893360150)\n" +
                "TRANSFER X MOBILE - Virement Mobile Money\n" +
                "HELP - Cette aide";
    }

    private String handleUnknownCommand(String command) {
        log.warn("Commande inconnue: {}", command);
        return String.format("%s - Commande inconnue. Tapez HELP pour la liste des commandes.",
                smsPrefix);
    }
}
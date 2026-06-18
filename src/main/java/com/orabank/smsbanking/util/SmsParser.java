package com.orabank.smsbanking.util;

import com.orabank.smsbanking.entity.enums.CommandType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility class for parsing SMS commands.
 * Identifies the command type from the SMS message content.
 */
@Slf4j
@Component
public class SmsParser {

    // ============================================================
    // PATTERNS
    // ============================================================

    private static final Pattern HISTORY_PATTERN = Pattern.compile("^HISTO(?:RIQUE)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP_PATTERN = Pattern.compile("^OTP$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "^TRANSFER\\s+\\d+(?:\\s+\\w+)?(?:\\s+\\+?\\d+)?(?:\\s+\\w+)?(?:\\s+MOBILE)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HELP_PATTERN = Pattern.compile("^HELP$", Pattern.CASE_INSENSITIVE);

    /**
     * Parses the command type from the SMS message.
     * Accepte: SOLDE, SOLDE?, SOLDE COMPTEXXX, SOLDE? COMPTEXXX
     *
     * @param message the SMS message content
     * @return the identified command type
     */
    public CommandType parseCommand(String message) {
        if (message == null || message.trim().isEmpty()) {
            log.debug("Empty message received, returning UNKNOWN command type");
            return CommandType.UNKNOWN;
        }

        String trimmedMessage = message.trim().toUpperCase();

        // ============================================================
        // VÉRIFICATION SPÉCIFIQUE POUR SOLDE (plus robuste)
        // Accepte: SOLDE, SOLDE?, SOLDE COMPTEXXX, SOLDE? COMPTEXXX
        // ============================================================
        if (trimmedMessage.startsWith("SOLDE")) {
            log.debug("Identified SOLDE command from: {}", trimmedMessage);
            return CommandType.SOLDE;
        }

        if (trimmedMessage.startsWith("HISTO")) {
            log.debug("Identified HISTORY command");
            return CommandType.HISTO;
        } else if (trimmedMessage.equals("OTP")) {
            log.debug("Identified OTP command");
            return CommandType.OTP;
        } else if (trimmedMessage.startsWith("TRANSFER")) {
            log.debug("Identified TRANSFER command");
            return CommandType.TRANSFER;
        } else if (trimmedMessage.equals("HELP")) {
            log.debug("Identified HELP command");
            return CommandType.HELP;
        } else {
            log.debug("Unknown command: {}", trimmedMessage);
            return CommandType.UNKNOWN;
        }
    }

    /**
     * Extracts the amount from a transfer command.
     *
     * @param message the transfer command message
     * @return the extracted amount, or null if not found
     */
    public Long extractTransferAmount(String message) {
        if (message == null) {
            return null;
        }

        try {
            String[] parts = message.trim().split("\\s+");
            if (parts.length >= 2) {
                String amountStr = parts[1];
                if (amountStr.matches("\\d+")) {
                    return Long.parseLong(amountStr);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse transfer amount from message: {}", message);
        }

        return null;
    }

    /**
     * Extracts the recipient phone number from a transfer command.
     *
     * @param message the transfer command message
     * @return the extracted recipient phone number, or null if not found
     */
    public String extractRecipientPhone(String message) {
        if (message == null) {
            return null;
        }

        String[] parts = message.trim().split("\\s+");

        for (String part : parts) {
            if (part.matches("^\\+?\\d{8,15}$")) {
                return part;
            }
        }

        return null;
    }

    /**
     * Extracts the source account number from a command.
     * Pour les transferts, retourne le premier compte trouvé (compte source).
     *
     * @param message the command message
     * @return the extracted account number, or null if not found
     */
    public String extractAccountNumber(String message) {
        if (message == null) {
            return null;
        }

        String[] parts = message.trim().split("\\s+");

        for (String part : parts) {
            if (part.matches("^COMPTE\\d+$")) {
                return part;
            }
        }

        return null;
    }

    /**
     * Extracts the target account number from a transfer command.
     * Le compte destinataire est le compte après le numéro de téléphone.
     *
     * @param message the transfer command message
     * @return the extracted target account number, or null if not found
     */
    public String extractTargetAccountNumber(String message) {
        if (message == null) {
            return null;
        }

        String[] parts = message.trim().split("\\s+");

        int phoneIndex = -1;

        // Trouver l'index du numéro de téléphone
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].matches("^\\+?\\d{8,15}$")) {
                phoneIndex = i;
                break;
            }
        }

        // Chercher un compte après le numéro de téléphone
        if (phoneIndex != -1) {
            for (int i = phoneIndex + 1; i < parts.length; i++) {
                if (parts[i].matches("^COMPTE\\d+$")) {
                    return parts[i];
                }
            }
        }

        return null;
    }

    /**
     * Checks if a transfer command specifies MOBILE transfer type.
     *
     * @param message the transfer command message
     * @return true if MOBILE is specified, false otherwise
     */
    public boolean isMobileTransfer(String message) {
        if (message == null) {
            return false;
        }

        return message.trim().toUpperCase().contains("MOBILE");
    }
}
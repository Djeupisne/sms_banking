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
    // PATTERNS CORRIGÉS
    // ============================================================

    // Accepte: SOLDE, SOLDE?, SOLDE COMPTEXXX, SOLDE? COMPTEXXX
    // CORRECTION: \\? pour échapper le point d'interrogation
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "^SOLDE\\??(?:\\s+\\w+)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HISTORY_PATTERN = Pattern.compile("^HISTO(?:RIQUE)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP_PATTERN = Pattern.compile("^OTP$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "^TRANSFER\\s+\\d+(?:\\s+\\w+)?(?:\\s+\\+?\\d+)?(?:\\s+MOBILE)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HELP_PATTERN = Pattern.compile("^HELP$", Pattern.CASE_INSENSITIVE);

    /**
     * Parses the command type from the SMS message.
     */
    public CommandType parseCommand(String message) {
        if (message == null || message.trim().isEmpty()) {
            log.debug("Empty message received, returning UNKNOWN command type");
            return CommandType.UNKNOWN;
        }

        String trimmedMessage = message.trim();

        // ============================================================
        // VÉRIFICATION SPÉCIFIQUE POUR SOLDE (plus robuste)
        // ============================================================
        // Vérifier si le message commence par SOLDE (insensible à la casse)
        // Cela gère: SOLDE, SOLDE?, SOLDE COMPTEXXX, SOLDE? COMPTEXXX
        String upperMessage = trimmedMessage.toUpperCase();
        if (upperMessage.startsWith("SOLDE")) {
            log.debug("Identified SOLDE command from: {}", trimmedMessage);
            return CommandType.SOLDE;
        }

        // Vérifier les autres commandes
        if (upperMessage.startsWith("HISTO")) {
            log.debug("Identified HISTORY command");
            return CommandType.HISTO;
        } else if (upperMessage.equals("OTP")) {
            log.debug("Identified OTP command");
            return CommandType.OTP;
        } else if (upperMessage.startsWith("TRANSFER")) {
            log.debug("Identified TRANSFER command");
            return CommandType.TRANSFER;
        } else if (upperMessage.equals("HELP")) {
            log.debug("Identified HELP command");
            return CommandType.HELP;
        } else {
            log.debug("Unknown command: {}", trimmedMessage);
            return CommandType.UNKNOWN;
        }
    }

    /**
     * Extracts the amount from a transfer command.
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
     * Extracts the account number from a command.
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
     * Checks if a transfer command specifies MOBILE transfer type.
     */
    public boolean isMobileTransfer(String message) {
        if (message == null) {
            return false;
        }

        return message.trim().toUpperCase().contains("MOBILE");
    }
}
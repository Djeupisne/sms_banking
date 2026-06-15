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
    
    private static final Pattern BALANCE_PATTERN = Pattern.compile("^SOLDE\\??$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HISTORY_PATTERN = Pattern.compile("^HISTO(?:RIQUE)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP_PATTERN = Pattern.compile("^OTP$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFER_PATTERN = Pattern.compile("^TRANSFER\\s+\\d+(?:\\s+\\+?\\d+)?(?:\\s+MOBILE)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HELP_PATTERN = Pattern.compile("^HELP$", Pattern.CASE_INSENSITIVE);
    
    /**
     * Parses the command type from the SMS message.
     *
     * @param message the SMS message content
     * @return the identified command type
     */
    public CommandType parseCommand(String message) {
        if (message == null || message.trim().isEmpty()) {
            log.debug("Empty message received, returning UNKNOWN command type");
            return CommandType.UNKNOWN;
        }
        
        String trimmedMessage = message.trim();
        
        if (BALANCE_PATTERN.matcher(trimmedMessage).matches()) {
            log.debug("Identified SOLDE command");
            return CommandType.SOLDE;
        } else if (HISTORY_PATTERN.matcher(trimmedMessage).matches()) {
            log.debug("Identified HISTORY command");
            return CommandType.HISTO;
        } else if (OTP_PATTERN.matcher(trimmedMessage).matches()) {
            log.debug("Identified OTP command");
            return CommandType.OTP;
        } else if (TRANSFER_PATTERN.matcher(trimmedMessage).matches()) {
            log.debug("Identified TRANSFER command");
            return CommandType.TRANSFER;
        } else if (HELP_PATTERN.matcher(trimmedMessage).matches()) {
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
        
        java.util.regex.Matcher matcher = TRANSFER_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            try {
                String[] parts = message.trim().split("\\s+");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[1]);
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse transfer amount from message: {}", message);
            }
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
        
        java.util.regex.Matcher matcher = TRANSFER_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            String[] parts = message.trim().split("\\s+");
            // TRANSFER 50000 -> no recipient (parts.length = 2)
            // TRANSFER 50000 +2250123456789 -> recipient (parts.length = 3)
            // TRANSFER 50000 +2250123456789 MOBILE -> recipient (parts.length = 4)
            if (parts.length >= 3 && !parts[2].equalsIgnoreCase("MOBILE")) {
                return parts[2];
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

package com.orabank.smsbanking.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for standardized logging operations.
 * Provides helper methods for consistent logging across the application.
 */
@Slf4j
public class LoggingUtil {
    
    /**
     * Masks sensitive information in phone numbers for logging.
     *
     * @param phoneNumber the phone number to mask
     * @return the masked phone number
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return phoneNumber.substring(0, Math.max(0, phoneNumber.length() - 4)) + "****";
    }
    
    /**
     * Masks sensitive information in account numbers for logging.
     *
     * @param accountNumber the account number to mask
     * @return the masked account number
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "***";
        }
        return accountNumber.substring(0, Math.max(0, accountNumber.length() - 4)) + "****";
    }
    
    /**
     * Logs an informational message with masked phone number.
     *
     * @param message the message format string
     * @param phoneNumber the phone number to mask in the log
     * @param params additional parameters for the message
     */
    public static void logWithMaskedPhone(String message, String phoneNumber, Object... params) {
        String maskedPhone = maskPhoneNumber(phoneNumber);
        log.info(message, insertMaskedPhone(params, phoneNumber, maskedPhone));
    }
    
    /**
     * Logs a warning message with masked phone number.
     *
     * @param message the message format string
     * @param phoneNumber the phone number to mask in the log
     * @param params additional parameters for the message
     */
    public static void logWarningWithMaskedPhone(String message, String phoneNumber, Object... params) {
        String maskedPhone = maskPhoneNumber(phoneNumber);
        log.warn(message, insertMaskedPhone(params, phoneNumber, maskedPhone));
    }
    
    private static Object[] insertMaskedPhone(Object[] params, String originalPhone, String maskedPhone) {
        Object[] newParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof String && params[i].equals(originalPhone)) {
                newParams[i] = maskedPhone;
            } else {
                newParams[i] = params[i];
            }
        }
        return newParams;
    }
}

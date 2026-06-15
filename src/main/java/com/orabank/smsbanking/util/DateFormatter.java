package com.orabank.smsbanking.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for date and time formatting operations.
 * Provides standardized date formatting for the application.
 */
public class DateFormatter {
    
    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Formats a LocalDateTime to the standard format (yyyy-MM-dd HH:mm:ss).
     *
     * @param dateTime the LocalDateTime to format
     * @return the formatted date-time string
     */
    public static String formatStandard(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(STANDARD_FORMATTER);
    }
    
    /**
     * Formats a LocalDateTime to date-only format (yyyy-MM-dd).
     *
     * @param dateTime the LocalDateTime to format
     * @return the formatted date string
     */
    public static String formatDateOnly(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_ONLY_FORMATTER);
    }
    
    /**
     * Formats a LocalDateTime to time-only format (HH:mm:ss).
     *
     * @param dateTime the LocalDateTime to format
     * @return the formatted time string
     */
    public static String formatTimeOnly(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(TIME_ONLY_FORMATTER);
    }
    
    /**
     * Parses a date-time string in standard format to LocalDateTime.
     *
     * @param dateString the date-time string to parse
     * @return the parsed LocalDateTime, or null if parsing fails
     */
    public static LocalDateTime parseStandard(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateString, STANDARD_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}

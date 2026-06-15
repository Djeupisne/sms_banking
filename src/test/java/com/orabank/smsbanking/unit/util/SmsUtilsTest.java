package com.orabank.smsbanking.unit.util;

import com.orabank.smsbanking.util.SmsUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la classe SmsUtils.
 * Vérifie la normalisation des numéros de téléphone.
 */
class SmsUtilsTest {

    @Test
    void testNormalizePhoneNumber_withPlusPrefix() {
        // Given
        String phoneNumber = "+2250123456789";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+2250123456789", normalized);
    }

    @Test
    void testNormalizePhoneNumber_withoutPlusPrefix() {
        // Given
        String phoneNumber = "2250123456789";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+2250123456789", normalized);
    }

    @Test
    void testNormalizePhoneNumber_withLocalFormat() {
        // Given
        String phoneNumber = "0123456789";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+225123456789", normalized);
    }

    @Test
    void testNormalizePhoneNumber_withSpaces() {
        // Given
        String phoneNumber = "01 23 45 67 89";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+225123456789", normalized);
    }

    @Test
    void testNormalizePhoneNumber_withPlusAndSpaces() {
        // Given
        String phoneNumber = "+225 01 23 45 67 89";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+2250123456789", normalized);
    }

    @Test
    void testNormalizePhoneNumber_nullInput() {
        // When
        String normalized = SmsUtils.normalizePhoneNumber(null);

        // Then
        assertNull(normalized);
    }

    @Test
    void testNormalizePhoneNumber_emptyInput() {
        // When
        String normalized = SmsUtils.normalizePhoneNumber("");

        // Then
        assertNull(normalized);
    }

    @Test
    void testNormalizePhoneNumber_whitespaceInput() {
        // When
        String normalized = SmsUtils.normalizePhoneNumber("   ");

        // Then
        assertNull(normalized);
    }

    @Test
    void testNormalizePhoneNumber_shortNumber() {
        // Given
        String phoneNumber = "12345";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+22512345", normalized);
    }

    @Test
    void testNormalizePhoneNumber_numberWithoutCountryCode() {
        // Given
        String phoneNumber = "0700000001";

        // When
        String normalized = SmsUtils.normalizePhoneNumber(phoneNumber);

        // Then
        assertEquals("+225700000001", normalized);
    }
}

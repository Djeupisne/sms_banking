package com.orabank.smsbanking.unit.util;

import com.orabank.smsbanking.entity.enums.CommandType;
import com.orabank.smsbanking.util.SmsParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour SmsParser.
 * Couvre tous les cas de parsing des commandes SMS, y compris les cas limites et erreurs.
 */
class SmsParserTest {

    private SmsParser smsParser;

    @BeforeEach
    void setUp() {
        smsParser = new SmsParser();
    }

    // ==================== Tests parseCommand ====================

    @Test
    @DisplayName("SOLDE? - devrait retourner SOLDE")
    void testParseCommand_Solde_WithQuestionMark() {
        assertEquals(CommandType.SOLDE, smsParser.parseCommand("SOLDE?"));
        assertEquals(CommandType.SOLDE, smsParser.parseCommand("solde?"));
        assertEquals(CommandType.SOLDE, smsParser.parseCommand("Solde?"));
        assertEquals(CommandType.SOLDE, smsParser.parseCommand("  SOLDE?  "));
    }

    @Test
    @DisplayName("HISTO - devrait retourner HISTO")
    void testParseCommand_Histo() {
        assertEquals(CommandType.HISTO, smsParser.parseCommand("HISTO"));
        assertEquals(CommandType.HISTO, smsParser.parseCommand("histo"));
        assertEquals(CommandType.HISTO, smsParser.parseCommand("Histo"));
        assertEquals(CommandType.HISTO, smsParser.parseCommand("  HISTO  "));
    }

    @Test
    @DisplayName("OTP - devrait retourner OTP")
    void testParseCommand_Otp() {
        assertEquals(CommandType.OTP, smsParser.parseCommand("OTP"));
        assertEquals(CommandType.OTP, smsParser.parseCommand("otp"));
        assertEquals(CommandType.OTP, smsParser.parseCommand("Otp"));
    }

    @Test
    @DisplayName("HELP - devrait retourner HELP")
    void testParseCommand_Help() {
        assertEquals(CommandType.HELP, smsParser.parseCommand("HELP"));
        assertEquals(CommandType.HELP, smsParser.parseCommand("help"));
        assertEquals(CommandType.HELP, smsParser.parseCommand("Help"));
    }

    @ParameterizedTest
    @CsvSource({
        "'TRANSFER 50000', TRANSFER",
        "'TRANSFER 10000 +2250123456789', TRANSFER",
        "'transfer 50000', TRANSFER",
        "'Transfer 50000 +2250123456789 MOBILE', TRANSFER",
        "'TRANSFER   50000   +2250123456789', TRANSFER"
    })
    @DisplayName("TRANSFER - devrait retourner TRANSFER")
    void testParseCommand_Transfer(String message, CommandType expected) {
        assertEquals(expected, smsParser.parseCommand(message));
    }

    @Test
    @DisplayName("Commandes inconnues - devrait retourner UNKNOWN")
    void testParseCommand_Unknown() {
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("UNKNOWN"));
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("RANDOM"));
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("SOLDE sans point d'interrogation"));
        // HISTORIQUE est maintenant reconnu comme HISTO grâce au pattern HISTO(?:RIQUE)?
        assertEquals(CommandType.HISTO, smsParser.parseCommand("HISTORIQUE"));
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("TRANSFERT 50000"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Message null ou vide - devrait retourner UNKNOWN")
    void testParseCommand_NullOrEmpty(String message) {
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand(message));
    }

    @Test
    @DisplayName("Message avec espaces uniquement - devrait retourner UNKNOWN")
    void testParseCommand_OnlySpaces() {
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("   "));
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("\t\n"));
    }

    // ==================== Tests extractTransferAmount ====================

    @ParameterizedTest
    @CsvSource({
        "'TRANSFER 50000', 50000",
        "'transfer 999999', 999999"
    })
    @DisplayName("Extraction du montant - cas valides")
    void testExtractTransferAmount_Valid(String message, long expected) {
        assertEquals(expected, smsParser.extractTransferAmount(message));
    }

    @ParameterizedTest
    @CsvSource({
        "'TRANSFER 10000 +2250123456789', 10000",
        "'TRANSFER 1000 +2250123456789 MOBILE', 1000"
    })
    @DisplayName("Extraction du montant - avec destinataire")
    void testExtractTransferAmount_WithRecipient(String message, long expected) {
        assertEquals(expected, smsParser.extractTransferAmount(message));
    }

    @Test
    @DisplayName("Extraction du montant - message null")
    void testExtractTransferAmount_Null() {
        assertNull(smsParser.extractTransferAmount(null));
    }

    @Test
    @DisplayName("Extraction du montant - format invalide")
    void testExtractTransferAmount_InvalidFormat() {
        assertNull(smsParser.extractTransferAmount("SOLDE?"));
        assertNull(smsParser.extractTransferAmount("HISTO"));
        assertNull(smsParser.extractTransferAmount("TRANSFER"));
        assertNull(smsParser.extractTransferAmount("TRANSFER ABC"));
    }

    // ==================== Tests extractRecipientPhone ====================

    @ParameterizedTest
    @CsvSource({
        "'TRANSFER 50000 +2250123456789', '+2250123456789'",
        "'TRANSFER 10000 +2250714123456 MOBILE', '+2250714123456'",
        "'TRANSFER 5000 +2250123456789', '+2250123456789'"
    })
    @DisplayName("Extraction du numéro - cas valides")
    void testExtractRecipientPhone_Valid(String message, String expected) {
        assertEquals(expected, smsParser.extractRecipientPhone(message));
    }

    @Test
    @DisplayName("Extraction du numéro - pas de destinataire")
    void testExtractRecipientPhone_NoRecipient() {
        assertNull(smsParser.extractRecipientPhone("TRANSFER 50000"));
        assertNull(smsParser.extractRecipientPhone("SOLDE?"));
        assertNull(smsParser.extractRecipientPhone(null));
    }

    @Test
    @DisplayName("Extraction du numéro - format MOBILE sans numéro")
    void testExtractRecipientPhone_MobileKeywordOnly() {
        // Cas edge: TRANSFER 50000 MOBILE (sans numéro intermédiaire)
        // Le parser actuel ne matchera pas ce pattern car il attend un numéro
        assertNull(smsParser.extractRecipientPhone("TRANSFER 50000 MOBILE"));
    }

    // ==================== Tests isMobileTransfer ====================

    @ParameterizedTest
    @CsvSource({
        "'TRANSFER 50000 +2250123456789 MOBILE', true",
        "'TRANSFER 50000 mobile', true",
        "'TRANSFER 50000 Mobile', true",
        "'TRANSFER 50000 +2250123456789', false",
        "'TRANSFER 50000', false"
    })
    @DisplayName("Détection transfert Mobile Money")
    void testIsMobileTransfer(String message, boolean expected) {
        assertEquals(expected, smsParser.isMobileTransfer(message));
    }

    @Test
    @DisplayName("isMobileTransfer - message null")
    void testIsMobileTransfer_Null() {
        assertFalse(smsParser.isMobileTransfer(null));
    }

    // ==================== Tests de robustesse et cas limites ====================

    @Test
    @DisplayName("Caractères spéciaux dans le message")
    void testParseCommand_SpecialCharacters() {
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("SOLDE?!"));
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("HISTO!"));
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand("OTP@"));
    }

    @Test
    @DisplayName("Montants limites")
    void testExtractTransferAmount_EdgeCases() {
        // TRANSFER 0 est maintenant valide avec le nouveau pattern
        assertEquals(0L, smsParser.extractTransferAmount("TRANSFER 0"));
        assertEquals(1L, smsParser.extractTransferAmount("TRANSFER 1"));
        assertEquals(999999999L, smsParser.extractTransferAmount("TRANSFER 999999999"));
    }

    @Test
    @DisplayName("Numéros de téléphone avec différents formats")
    void testExtractRecipientPhone_DifferentFormats() {
        assertEquals("+2250123456789", smsParser.extractRecipientPhone("TRANSFER 50000 +2250123456789"));
        assertEquals("0123456789", smsParser.extractRecipientPhone("TRANSFER 50000 0123456789"));
        assertEquals("2250123456789", smsParser.extractRecipientPhone("TRANSFER 50000 2250123456789"));
    }

    @Test
    @DisplayName("Messages très longs")
    void testParseCommand_VeryLongMessage() {
        String longMessage = "SOLDE?" + " ".repeat(1000);
        assertEquals(CommandType.SOLDE, smsParser.parseCommand(longMessage));
        
        String invalidLongMessage = "COMMAND_INCONNUE_" + "A".repeat(500);
        assertEquals(CommandType.UNKNOWN, smsParser.parseCommand(invalidLongMessage));
    }

    @Test
    @DisplayName("Transfert avec plusieurs espaces")
    void testParseCommand_Transfer_MultipleSpaces() {
        assertEquals(CommandType.TRANSFER, smsParser.parseCommand("TRANSFER    50000    +2250123456789"));
        assertEquals(CommandType.TRANSFER, smsParser.parseCommand("  TRANSFER   50000   +2250123456789  "));
    }

    @Test
    @DisplayName("Cas mixtes - minuscules/majuscules")
    void testParseCommand_MixedCase() {
        assertEquals(CommandType.SOLDE, smsParser.parseCommand("SoLdE?"));
        assertEquals(CommandType.HISTO, smsParser.parseCommand("HiStO"));
        assertEquals(CommandType.TRANSFER, smsParser.parseCommand("TrAnSfEr 50000"));
    }
}

package com.orabank.smsbanking.unit.service;

import com.orabank.smsbanking.entity.enums.CommandType;
import com.orabank.smsbanking.service.AccountService;
import com.orabank.smsbanking.service.CommandHandlerService;
import com.orabank.smsbanking.service.MobileMoneyService;
import com.orabank.smsbanking.security.OtpGenerator;
import com.orabank.smsbanking.util.SmsParser;
import com.orabank.smsbanking.security.PhoneVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandHandlerServiceTest {

    @Mock
    private AccountService accountService;
    
    @Mock
    private OtpGenerator otpGenerator;
    
    @Mock
    private MobileMoneyService mobileMoneyService;
    
    @Mock
    private SmsParser smsParser;
    
    @Mock
    private PhoneVerificationService phoneVerificationService;
    
    private CommandHandlerService commandHandlerService;

    @BeforeEach
    void setUp() {
        commandHandlerService = new CommandHandlerService(
            accountService, 
            otpGenerator, 
            mobileMoneyService,
            smsParser,
            phoneVerificationService
        );
    }

    @Test
    void testHandleBalanceInquiryCommand() {
        // Given
        String phoneNumber = "+2250123456789";
        BigDecimal balance = new BigDecimal("500000.00");
        when(accountService.getBalance(eq(phoneNumber))).thenReturn(balance);

        // When
        String response = commandHandlerService.handleCommand(
            "SOLDE", 
            phoneNumber, 
            "SOLDE?"
        );

        // Then
        assertTrue(response.contains("500000"), "Response should contain balance amount");
        verify(accountService, times(1)).getBalance(eq(phoneNumber));
    }

    @Test
    void testHandleOtpCommand() {
        // Given
        String phoneNumber = "+2250123456789";
        when(phoneVerificationService.generateAndSendOtp(eq(phoneNumber))).thenReturn(true);

        // When
        String response = commandHandlerService.handleCommand(
            "OTP", 
            phoneNumber, 
            "OTP"
        );

        // Then
        assertTrue(response.contains("OTP"), "Response should mention OTP");
        verify(phoneVerificationService, times(1)).generateAndSendOtp(eq(phoneNumber));
    }

    @Test
    void testHandleUnknownCommand() {
        // Given
        String phoneNumber = "+2250123456789";

        // When
        String response = commandHandlerService.handleCommand(
            "UNKNOWN", 
            phoneNumber, 
            "UNKNOWN_COMMAND"
        );

        // Then
        assertTrue(response.contains("Commande inconnue"), "Response should indicate unknown command");
        assertTrue(response.contains("HELP"), "Response should suggest HELP command");
    }

    @Test
    void testHandleTransferCommandSuccess() {
        // Given
        String phoneNumber = "+2250123456789";
        String recipientPhone = "+2250987654321";
        long amount = 10000;
        BigDecimal balance = new BigDecimal("500000.00");
        com.orabank.smsbanking.entity.Transaction mockTransaction = 
            new com.orabank.smsbanking.entity.Transaction();
        
        when(smsParser.extractTransferAmount("TRANSFER 10000 +2250987654321"))
            .thenReturn(amount);
        when(smsParser.extractRecipientPhone("TRANSFER 10000 +2250987654321"))
            .thenReturn(recipientPhone);
        when(smsParser.isMobileTransfer("TRANSFER 10000 +2250987654321"))
            .thenReturn(false);
        when(accountService.getBalance(eq(phoneNumber))).thenReturn(balance);
        when(accountService.transfer(eq(phoneNumber), eq(recipientPhone), any(BigDecimal.class), any(String.class)))
            .thenReturn(mockTransaction);

        // When
        String response = commandHandlerService.handleCommand(
            "TRANSFER", 
            phoneNumber, 
            "TRANSFER 10000 +2250987654321"
        );

        // Then
        assertTrue(response.contains("10000"), "Response should contain transfer amount");
        verify(accountService, times(1)).transfer(eq(phoneNumber), eq(recipientPhone), any(BigDecimal.class), any(String.class));
    }

    @Test
    void testHandleHelpCommand() {
        // Given
        String phoneNumber = "+2250123456789";

        // When
        String response = commandHandlerService.handleCommand(
            "HELP", 
            phoneNumber, 
            "HELP"
        );

        // Then
        assertTrue(response.contains("Commandes disponibles:"), "Response should list available commands");
        assertTrue(response.contains("SOLDE"), "Help should include SOLDE command");
        assertTrue(response.contains("HISTO"), "Help should include HISTO command");
        assertTrue(response.contains("OTP"), "Help should include OTP command");
        assertTrue(response.contains("TRANSFER"), "Help should include TRANSFER command");
        assertTrue(response.contains("HELP"), "Help should include HELP command");
    }

    @Test
    void testHandleTransferToSelfShouldFail() {
        // Given
        String phoneNumber = "+22890000004";
        long amount = 20000;
        BigDecimal balance = new BigDecimal("500000.00");
        
        when(smsParser.extractTransferAmount("TRANSFER 20000 +22890000004"))
            .thenReturn(amount);
        when(smsParser.extractRecipientPhone("TRANSFER 20000 +22890000004"))
            .thenReturn(phoneNumber);
        when(accountService.getBalance(eq(phoneNumber))).thenReturn(balance);

        // When
        String response = commandHandlerService.handleCommand(
            "TRANSFER", 
            phoneNumber, 
            "TRANSFER 20000 +22890000004"
        );

        // Then - Le transfert vers soi-même doit être rejeté
        assertTrue(response.contains("Impossible"), "Response should indicate impossibility");
        assertTrue(response.contains("propre compte"), "Response should mention own account");
        verify(accountService, never()).transfer(any(), any(), any(), any());
    }
}
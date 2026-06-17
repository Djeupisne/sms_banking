package com.orabank.smsbanking.unit.service;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.service.AccountService;
import com.orabank.smsbanking.service.CommandHandlerService;
import com.orabank.smsbanking.service.MobileMoneyService;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandHandlerServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private MobileMoneyService mobileMoneyService;

    @Mock
    private SmsParser smsParser;

    @Mock
    private PhoneVerificationService phoneVerificationService;

    private CommandHandlerService commandHandlerService;

    @BeforeEach
    void setUp() {
        // Nouveau constructeur avec 4 paramètres (sans OtpGenerator)
        commandHandlerService = new CommandHandlerService(
                accountService,
                phoneVerificationService,
                mobileMoneyService,
                smsParser
        );
    }

    // ============================================================
    // TESTS POUR LA COMMANDE SOLDE
    // ============================================================

    @Test
    void testHandleBalanceInquiryCommand_SingleAccount() {
        // Given
        String phoneNumber = "+22890000002";
        BigDecimal balance = new BigDecimal("750000.00");

        List<Account> accounts = new ArrayList<>();
        Account account = Account.builder()
                .accountNumber("COMPTE002")
                .balance(balance)
                .build();
        accounts.add(account);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);

        // When
        String response = commandHandlerService.handleCommand(
                "SOLDE",
                phoneNumber,
                "SOLDE?"
        );

        // Then
        assertTrue(response.contains("750000"), "Response should contain balance amount");
        assertTrue(response.contains("FCFA"), "Response should contain currency");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
    }

    @Test
    void testHandleBalanceInquiryCommand_MultipleAccounts() {
        // Given
        String phoneNumber = "+22890000002";

        List<Account> accounts = new ArrayList<>();
        Account account1 = Account.builder()
                .accountNumber("COMPTE002")
                .balance(new BigDecimal("750000.00"))
                .build();
        Account account2 = Account.builder()
                .accountNumber("COMPTE005")
                .balance(new BigDecimal("228000.00"))
                .build();
        accounts.add(account1);
        accounts.add(account2);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);

        // When
        String response = commandHandlerService.handleCommand(
                "SOLDE",
                phoneNumber,
                "SOLDE?"
        );

        // Then
        assertTrue(response.contains("Vos comptes"), "Response should list accounts");
        assertTrue(response.contains("COMPTE002"), "Response should contain COMPTE002");
        assertTrue(response.contains("COMPTE005"), "Response should contain COMPTE005");
        assertTrue(response.contains("750000"), "Response should contain COMPTE002 balance");
        assertTrue(response.contains("228000"), "Response should contain COMPTE005 balance");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
    }

    @Test
    void testHandleBalanceInquiryCommand_WithSpecificAccount() {
        // Given
        String phoneNumber = "+22890000002";
        String accountNumber = "COMPTE002";
        BigDecimal balance = new BigDecimal("750000.00");

        List<Account> accounts = new ArrayList<>();
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .balance(balance)
                .build();
        accounts.add(account);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);

        // When
        String response = commandHandlerService.handleCommand(
                "SOLDE",
                phoneNumber,
                "SOLDE? COMPTE002"
        );

        // Then
        assertTrue(response.contains("750000"), "Response should contain balance");
        assertTrue(response.contains("COMPTE002"), "Response should contain account number");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
    }

    @Test
    void testHandleBalanceInquiryCommand_ClientNotFound() {
        // Given
        String phoneNumber = "+22899999999";

        when(accountService.getAccountsByPhone(eq(phoneNumber)))
                .thenThrow(new ClientNotFoundException("Client non trouvé"));

        // When
        String response = commandHandlerService.handleCommand(
                "SOLDE",
                phoneNumber,
                "SOLDE?"
        );

        // Then
        assertTrue(response.contains("Client non trouvé"), "Response should indicate client not found");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
    }

    // ============================================================
    // TESTS POUR LA COMMANDE HISTO
    // ============================================================

    @Test
    void testHandleHistoryCommand_SingleAccount() {
        // Given
        String phoneNumber = "+22890000002";

        List<Account> accounts = new ArrayList<>();
        Account account = Account.builder()
                .accountNumber("COMPTE002")
                .build();
        accounts.add(account);

        List<Transaction> transactions = new ArrayList<>();
        Transaction tx1 = new Transaction();
        tx1.setType("CREDIT");
        tx1.setAmount(new BigDecimal("100000.00"));
        transactions.add(tx1);
        Transaction tx2 = new Transaction();
        tx2.setType("DEBIT");
        tx2.setAmount(new BigDecimal("5000.00"));
        transactions.add(tx2);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);
        when(accountService.getLastTransactions(eq(phoneNumber), eq("COMPTE002"), eq(5)))
                .thenReturn(transactions);

        // When
        String response = commandHandlerService.handleCommand(
                "HISTO",
                phoneNumber,
                "HISTO"
        );

        // Then
        assertTrue(response.contains("Dernieres transactions"), "Response should indicate history");
        assertTrue(response.contains("COMPTE002"), "Response should contain account number");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
        verify(accountService, times(1)).getLastTransactions(eq(phoneNumber), eq("COMPTE002"), eq(5));
    }

    @Test
    void testHandleHistoryCommand_MultipleAccounts() {
        // Given
        String phoneNumber = "+22890000002";

        List<Account> accounts = new ArrayList<>();
        Account account1 = Account.builder().accountNumber("COMPTE002").build();
        Account account2 = Account.builder().accountNumber("COMPTE005").build();
        accounts.add(account1);
        accounts.add(account2);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);

        // When
        String response = commandHandlerService.handleCommand(
                "HISTO",
                phoneNumber,
                "HISTO"
        );

        // Then
        assertTrue(response.contains("Plusieurs comptes trouvés"), "Response should indicate multiple accounts");
        assertTrue(response.contains("COMPTE002"), "Response should list COMPTE002");
        assertTrue(response.contains("COMPTE005"), "Response should list COMPTE005");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
        verify(accountService, never()).getLastTransactions(any(), any(), anyInt());
    }

    // ============================================================
    // TESTS POUR LA COMMANDE OTP
    // ============================================================

    @Test
    void testHandleOtpCommand_Success() {
        // Given
        String phoneNumber = "+22890000002";
        when(phoneVerificationService.generateAndSendOtp(eq(phoneNumber))).thenReturn(true);

        // When
        String response = commandHandlerService.handleCommand(
                "OTP",
                phoneNumber,
                "OTP"
        );

        // Then
        assertTrue(response.contains("OTP"), "Response should mention OTP");
        assertTrue(response.contains("envoyé"), "Response should indicate OTP sent");
        verify(phoneVerificationService, times(1)).generateAndSendOtp(eq(phoneNumber));
    }

    @Test
    void testHandleOtpCommand_Failure() {
        // Given
        String phoneNumber = "+22890000002";
        when(phoneVerificationService.generateAndSendOtp(eq(phoneNumber))).thenReturn(false);

        // When
        String response = commandHandlerService.handleCommand(
                "OTP",
                phoneNumber,
                "OTP"
        );

        // Then
        assertTrue(response.contains("Erreur"), "Response should indicate error");
        verify(phoneVerificationService, times(1)).generateAndSendOtp(eq(phoneNumber));
    }

    // ============================================================
    // TESTS POUR LA COMMANDE TRANSFERT
    // ============================================================

    @Test
    void testHandleTransferCommand_Success() {
        // Given
        String phoneNumber = "+22890000002";
        String recipientPhone = "+22890000003";
        long amount = 50000;

        List<Account> accounts = new ArrayList<>();
        Account account = Account.builder()
                .accountNumber("COMPTE002")
                .balance(new BigDecimal("750000.00"))
                .build();
        accounts.add(account);

        Transaction mockTransaction = new Transaction();

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);
        when(smsParser.extractTransferAmount(anyString())).thenReturn(amount);
        when(smsParser.extractRecipientPhone(anyString())).thenReturn(recipientPhone);
        when(accountService.transferFromAccount(any(Account.class), eq(recipientPhone), any(BigDecimal.class), anyString()))
                .thenReturn(mockTransaction);

        // When
        String response = commandHandlerService.handleCommand(
                "TRANSFER",
                phoneNumber,
                "TRANSFERT 50000 COMPTE002 +22890000003"
        );

        // Then
        assertTrue(response.contains("50000"), "Response should contain transfer amount");
        assertTrue(response.contains("COMPTE002"), "Response should contain source account");
        verify(accountService, times(1)).getAccountsByPhone(eq(phoneNumber));
        verify(accountService, times(1)).transferFromAccount(any(Account.class), eq(recipientPhone), any(BigDecimal.class), anyString());
    }

    @Test
    void testHandleTransferCommand_InsufficientBalance() {
        // Given
        String phoneNumber = "+22890000002";
        String recipientPhone = "+22890000003";
        long amount = 1000000;

        List<Account> accounts = new ArrayList<>();
        Account account = Account.builder()
                .accountNumber("COMPTE002")
                .balance(new BigDecimal("750000.00"))
                .build();
        accounts.add(account);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);
        when(smsParser.extractTransferAmount(anyString())).thenReturn(amount);
        when(smsParser.extractRecipientPhone(anyString())).thenReturn(recipientPhone);

        // When
        String response = commandHandlerService.handleCommand(
                "TRANSFER",
                phoneNumber,
                "TRANSFERT 1000000 COMPTE002 +22890000003"
        );

        // Then
        assertTrue(response.contains("Solde insuffisant"), "Response should indicate insufficient balance");
        assertTrue(response.contains("COMPTE002"), "Response should contain account");
        verify(accountService, never()).transferFromAccount(any(), any(), any(), any());
    }

    @Test
    void testHandleTransferToSelfShouldFail() {
        // Given
        String phoneNumber = "+22890000002";
        long amount = 20000;

        List<Account> accounts = new ArrayList<>();
        Account account = Account.builder()
                .accountNumber("COMPTE002")
                .balance(new BigDecimal("750000.00"))
                .build();
        accounts.add(account);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);
        when(smsParser.extractTransferAmount(anyString())).thenReturn(amount);
        when(smsParser.extractRecipientPhone(anyString())).thenReturn(phoneNumber);

        // When
        String response = commandHandlerService.handleCommand(
                "TRANSFER",
                phoneNumber,
                "TRANSFERT 20000 COMPTE002 +22890000002"
        );

        // Then
        assertTrue(response.contains("Impossible"), "Response should indicate impossibility");
        assertTrue(response.contains("propre compte"), "Response should mention own account");
        verify(accountService, never()).transferFromAccount(any(), any(), any(), any());
    }

    @Test
    void testHandleTransferCommand_MultipleAccountsWithoutSpecification() {
        // Given
        String phoneNumber = "+22890000002";
        long amount = 50000;

        List<Account> accounts = new ArrayList<>();
        Account account1 = Account.builder().accountNumber("COMPTE002").build();
        Account account2 = Account.builder().accountNumber("COMPTE005").build();
        accounts.add(account1);
        accounts.add(account2);

        when(accountService.getAccountsByPhone(eq(phoneNumber))).thenReturn(accounts);
        when(smsParser.extractTransferAmount(anyString())).thenReturn(amount);
        when(smsParser.extractRecipientPhone(anyString())).thenReturn("+22890000003");

        // When
        String response = commandHandlerService.handleCommand(
                "TRANSFER",
                phoneNumber,
                "TRANSFERT 50000 +22890000003"
        );

        // Then
        assertTrue(response.contains("Plusieurs comptes trouvés"), "Response should indicate multiple accounts");
        assertTrue(response.contains("COMPTE002"), "Response should list COMPTE002");
        assertTrue(response.contains("COMPTE005"), "Response should list COMPTE005");
        verify(accountService, never()).transferFromAccount(any(), any(), any(), any());
    }

    // ============================================================
    // TESTS POUR LES COMMANDES HELP ET UNKNOWN
    // ============================================================

    @Test
    void testHandleHelpCommand() {
        // Given
        String phoneNumber = "+22890000002";

        // When
        String response = commandHandlerService.handleCommand(
                "HELP",
                phoneNumber,
                "HELP"
        );

        // Then
        assertTrue(response.contains("Commandes disponibles:"), "Response should list available commands");
        assertTrue(response.contains("SOLDE?"), "Help should include SOLDE command");
        assertTrue(response.contains("HISTO"), "Help should include HISTO command");
        assertTrue(response.contains("OTP"), "Help should include OTP command");
        assertTrue(response.contains("TRANSFERT"), "Help should include TRANSFER command");
        assertTrue(response.contains("HELP"), "Help should include HELP command");
    }

    @Test
    void testHandleUnknownCommand() {
        // Given
        String phoneNumber = "+22890000002";

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
}
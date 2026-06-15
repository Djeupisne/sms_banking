package com.orabank.smsbanking.unit.service;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.exception.ClientNotFoundException;
import com.orabank.smsbanking.exception.InsufficientBalanceException;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository; // ✅ remplace EncryptionService
import com.orabank.smsbanking.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private TransactionRepository transactionRepository; // ✅ était EncryptionService

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, clientRepository, transactionRepository); // ✅ corrigé
    }

    @Test
    void testGetBalanceSuccess() {
        // Given
        String phoneNumber = "+2250123456789";
        Client client = Client.builder().id(1L).phoneNumber(phoneNumber).build();
        Account account = Account.builder()
            .id(1L)
            .clientId(1L)
            .balance(new BigDecimal("500000.00"))
            .build();

        when(clientRepository.findByPhoneNumber(eq(phoneNumber))).thenReturn(Optional.of(client));
        when(accountRepository.findByClientId(eq(1L))).thenReturn(Optional.of(account));

        // When
        BigDecimal balance = accountService.getBalance(phoneNumber);

        // Then
        assertEquals(new BigDecimal("500000.00"), balance);
        verify(clientRepository, times(1)).findByPhoneNumber(eq(phoneNumber));
        verify(accountRepository, times(1)).findByClientId(eq(1L));
    }

    @Test
    void testGetBalanceClientNotFound() {
        // Given
        String phoneNumber = "+2250123456789";
        when(clientRepository.findByPhoneNumber(eq(phoneNumber))).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ClientNotFoundException.class, () -> {
            accountService.getBalance(phoneNumber);
        });
    }

    @Test
    void testDebitSuccess() {
        // Given
        String phoneNumber = "+2250123456789";
        Client client = Client.builder().id(1L).phoneNumber(phoneNumber).build();
        Account account = Account.builder()
            .id(1L)
            .clientId(1L)
            .balance(new BigDecimal("500000.00"))
            .build();

        when(clientRepository.findByPhoneNumber(eq(phoneNumber))).thenReturn(Optional.of(client));
        when(accountRepository.findByClientId(eq(1L))).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        accountService.debit(phoneNumber, new BigDecimal("100000.00"), "Test debit");

        // Then
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testCreditSuccess() {
        // Given
        String phoneNumber = "+2250123456789";
        Client client = Client.builder().id(1L).phoneNumber(phoneNumber).build();
        Account account = Account.builder()
            .id(1L)
            .clientId(1L)
            .balance(new BigDecimal("400000.00"))
            .build();

        when(clientRepository.findByPhoneNumber(eq(phoneNumber))).thenReturn(Optional.of(client));
        when(accountRepository.findByClientId(eq(1L))).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        accountService.credit(phoneNumber, new BigDecimal("100000.00"), "Test credit");

        // Then
        verify(accountRepository, times(1)).save(any(Account.class));
    }
}

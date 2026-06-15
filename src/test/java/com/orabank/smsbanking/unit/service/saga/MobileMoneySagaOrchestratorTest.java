package com.orabank.smsbanking.unit.service.saga;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import com.orabank.smsbanking.gateway.CoreBankingApiClient;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.service.saga.MobileMoneySagaContext;
import com.orabank.smsbanking.service.saga.MobileMoneySagaOrchestrator;
import com.orabank.smsbanking.service.saga.SagaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour MobileMoneySagaOrchestrator.
 * Couvre les scénarios de succès, échec et compensation.
 */
@ExtendWith(MockitoExtension.class)
class MobileMoneySagaOrchestratorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CoreBankingApiClient coreBankingApiClient;

    private MobileMoneySagaOrchestrator orchestrator;

    private Client testClient;
    private Account testAccount;
    private static final String PHONE_NUMBER = "+2250123456789";
    private static final BigDecimal AMOUNT = new BigDecimal("50000");
    private static final String SAGA_ID = "saga-test-123";

    @BeforeEach
    void setUp() {
        orchestrator = new MobileMoneySagaOrchestrator(
                accountRepository, 
                clientRepository, 
                transactionRepository, 
                coreBankingApiClient
        );

        testClient = Client.builder()
                .id(1L)
                .phoneNumber(PHONE_NUMBER)
                .firstName("Test")
                .lastName("Client")
                .build();

        testAccount = Account.builder()
                .id(1L)
                .clientId(1L)
                .accountNumber("ACC12345678")
                .balance(new BigDecimal("100000"))
                .build();
    }

    @Test
    void testExecute_SuccessfulSaga() {
        // Given
        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreBankingApiClient.transferToMobileMoney(PHONE_NUMBER, 50000L)).thenReturn(true);

        // When
        MobileMoneySagaContext result = orchestrator.execute(context);

        // Then
        assertEquals(SagaState.COMPLETED, result.getState());
        assertNull(result.getErrorMessage());
        
        verify(accountRepository, times(1)).save(any(Account.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(coreBankingApiClient, times(1)).transferToMobileMoney(PHONE_NUMBER, 50000L);
        verify(clientRepository, times(1)).findByPhoneNumber(PHONE_NUMBER);
        verify(accountRepository, times(1)).findByClientId(1L);
    }

    @Test
    void testExecute_FailedBeforeCompensation_ClientNotFound() {
        // Given
        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.empty());

        // When
        MobileMoneySagaContext result = orchestrator.execute(context);

        // Then
        assertEquals(SagaState.FAILED_BEFORE_COMPENSATION, result.getState());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Client non trouvé"));
        
        verify(coreBankingApiClient, never()).transferToMobileMoney(any(), anyLong());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void testExecute_FailedBeforeCompensation_InsufficientBalance() {
        // Given
        Account lowBalanceAccount = Account.builder()
                .id(1L)
                .clientId(1L)
                .accountNumber("ACC12345678")
                .balance(new BigDecimal("10000")) // Solde insuffisant
                .build();

        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(lowBalanceAccount));

        // When
        MobileMoneySagaContext result = orchestrator.execute(context);

        // Then
        assertEquals(SagaState.FAILED_BEFORE_COMPENSATION, result.getState());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Solde insuffisant"));
        
        verify(coreBankingApiClient, never()).transferToMobileMoney(any(), anyLong());
    }

    @Test
    void testExecute_Compensation_Triggered_OnMobileMoneyFailure() {
        // Given
        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreBankingApiClient.transferToMobileMoney(PHONE_NUMBER, 50000L)).thenReturn(false);

        // When
        MobileMoneySagaContext result = orchestrator.execute(context);

        // Then
        assertEquals(SagaState.COMPENSATED, result.getState());
        assertNotNull(result.getErrorMessage());
        
        // Vérifier que le compte a été débité puis crédité (compensation)
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(coreBankingApiClient, times(1)).transferToMobileMoney(PHONE_NUMBER, 50000L);
    }

    @Test
    void testExecute_Compensation_Triggered_OnException() {
        // Given
        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coreBankingApiClient.transferToMobileMoney(PHONE_NUMBER, 50000L))
                .thenThrow(new RuntimeException("API unavailable"));

        // When
        MobileMoneySagaContext result = orchestrator.execute(context);

        // Then
        assertEquals(SagaState.COMPENSATED, result.getState());
        assertNotNull(result.getErrorMessage());
        
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void testDebitAccount_Success() {
        // Given
        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        MobileMoneySagaContext result = orchestrator.debitAccount(context);

        // Then
        assertEquals(SagaState.ACCOUNT_DEBITED, result.getState());
        assertNull(result.getErrorMessage());
        
        verify(accountRepository, times(1)).save(argThat(account -> 
                account.getBalance().equals(new BigDecimal("50000"))
        ));
        verify(transactionRepository, times(1)).save(argThat(tx -> 
                tx.getType().equals("DEBIT_MOBILE_MONEY") &&
                tx.getStatus() == TransactionStatus.PENDING
        ));
    }

    @Test
    void testDebitAccount_Fail_ClientNotFound() {
        // Given
        MobileMoneySagaContext context = createTestContext();
        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.empty());

        // When
        MobileMoneySagaContext result = orchestrator.debitAccount(context);

        // Then
        assertEquals(SagaState.FAILED_BEFORE_COMPENSATION, result.getState());
        assertNotNull(result.getErrorMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void testDebitAccount_Fail_InsufficientBalance() {
        // Given
        Account lowBalanceAccount = Account.builder()
                .id(1L)
                .clientId(1L)
                .balance(new BigDecimal("10000"))
                .build();

        MobileMoneySagaContext context = createTestContext();
        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(lowBalanceAccount));

        // When
        MobileMoneySagaContext result = orchestrator.debitAccount(context);

        // Then
        assertEquals(SagaState.FAILED_BEFORE_COMPENSATION, result.getState());
        assertTrue(result.getErrorMessage().contains("Solde insuffisant"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void testTransferToMobileMoney_Success() {
        // Given
        MobileMoneySagaContext context = createTestContext();
        when(coreBankingApiClient.transferToMobileMoney(PHONE_NUMBER, 50000L)).thenReturn(true);

        // When
        MobileMoneySagaContext result = orchestrator.transferToMobileMoney(context);

        // Then
        assertEquals(SagaState.COMPLETED, result.getState());
        verify(coreBankingApiClient, times(1)).transferToMobileMoney(PHONE_NUMBER, 50000L);
    }

    @Test
    void testTransferToMobileMoney_Failure_ReturnsFalse() {
        // Given
        MobileMoneySagaContext context = createTestContext();
        when(coreBankingApiClient.transferToMobileMoney(PHONE_NUMBER, 50000L)).thenReturn(false);

        // When
        MobileMoneySagaContext result = orchestrator.transferToMobileMoney(context);

        // Then
        assertEquals(SagaState.COMPENSATING, result.getState());
    }

    @Test
    void testTransferToMobileMoney_Failure_ThrowsException() {
        // Given
        MobileMoneySagaContext context = createTestContext();
        when(coreBankingApiClient.transferToMobileMoney(PHONE_NUMBER, 50000L))
                .thenThrow(new RuntimeException("Network error"));

        // When
        MobileMoneySagaContext result = orchestrator.transferToMobileMoney(context);

        // Then
        assertEquals(SagaState.COMPENSATING, result.getState());
    }

    @Test
    void testCompensate_Success() {
        // Given
        MobileMoneySagaContext context = createTestContext();

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        MobileMoneySagaContext result = orchestrator.compensate(context);

        // Then
        assertEquals(SagaState.COMPENSATED, result.getState());
        assertNull(result.getErrorMessage());
        
        verify(accountRepository, times(1)).save(argThat(account -> 
                account.getBalance().equals(new BigDecimal("150000")) // 100000 + 50000
        ));
        verify(transactionRepository, times(1)).save(argThat(tx -> 
                tx.getType().equals("CREDIT_COMPENSATION") &&
                tx.getStatus() == TransactionStatus.COMPLETED
        ));
    }

    @Test
    void testCompensate_Fail_ClientNotFound() {
        // Given
        MobileMoneySagaContext context = createTestContext();
        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.empty());

        // When
        MobileMoneySagaContext result = orchestrator.compensate(context);

        // Then
        assertNotEquals(SagaState.COMPENSATED, result.getState());
        assertNotNull(result.getErrorMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void testCompensate_MultipleAttempts() {
        // Given
        MobileMoneySagaContext context = createTestContext();
        context.setCompensationAttempts(2);

        when(clientRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testClient));
        when(accountRepository.findByClientId(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenThrow(new RuntimeException("DB error"));

        // When
        MobileMoneySagaContext result = orchestrator.compensate(context);

        // Then
        assertEquals(3, result.getCompensationAttempts());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("après 3 tentatives"));
    }

    private MobileMoneySagaContext createTestContext() {
        return MobileMoneySagaContext.create(PHONE_NUMBER, AMOUNT);
    }
}

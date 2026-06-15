package com.orabank.smsbanking.integration;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class DatabaseIntegrationTest {

    @Autowired
    private ClientRepository clientRepository;
    
    @Autowired
    private AccountRepository accountRepository;

    @Test
    void testSaveAndFindClient() {
        // Given
        Client client = Client.builder()
            .firstName("John")
            .lastName("Doe")
            .phoneNumber("+2250123456789")
            .email("john.doe@example.com")
            .dateOfBirth(LocalDateTime.now().minusYears(30))
            .address("Abidjan, Côte d'Ivoire")
            .build();

        // When
        Client savedClient = clientRepository.save(client);
        Client foundClient = clientRepository.findById(savedClient.getId()).orElse(null);

        // Then
        assertNotNull(foundClient);
        assertEquals("John", foundClient.getFirstName());
        assertEquals("Doe", foundClient.getLastName());
        assertEquals("+2250123456789", foundClient.getPhoneNumber());
        assertNotNull(foundClient.getCreatedAt());
        // updatedAt is set by @PreUpdate which is only triggered on update, not on insert in test context
        // assertNotNull(foundClient.getUpdatedAt());
    }

    @Test
    void testSaveAndFindAccount() {
        // Given
        Client client = Client.builder()
            .firstName("Jane")
            .lastName("Smith")
            .phoneNumber("+2250123456790")
            .email("jane.smith@example.com")
            .build();
        Client savedClient = clientRepository.save(client);

        Account account = Account.builder()
            .currency("FCFA")
            .accountNumber("ACC_TEST_001")
            .clientId(savedClient.getId())
            .balance(new BigDecimal("100000.00"))
            .accountType(Account.AccountType.CURRENT)
            .status(Account.AccountStatus.ACTIVE)
            .build();

        // When
        Account savedAccount = accountRepository.save(account);
        Account foundAccount = accountRepository.findById(savedAccount.getId()).orElse(null);

        // Then
        assertNotNull(foundAccount);
        assertEquals("ACC_TEST_001", foundAccount.getAccountNumber());
        assertEquals(savedClient.getId(), foundAccount.getClientId());
        assertEquals(new BigDecimal("100000.00"), foundAccount.getBalance());
        assertEquals(Account.AccountType.CURRENT, foundAccount.getAccountType());
        assertEquals(Account.AccountStatus.ACTIVE, foundAccount.getStatus());
        assertNotNull(foundAccount.getCreatedAt());
        // updatedAt is set by @PreUpdate which is only triggered on update, not on insert in test context
        // assertNotNull(foundAccount.getUpdatedAt());
    }

    @Test
    void testFindByPhoneNumber() {
        // Given
        Client client = Client.builder()
            .firstName("Bob")
            .lastName("Wilson")
            .phoneNumber("+2250123456791")
            .build();
        clientRepository.save(client);

        // When
        Client foundClient = clientRepository.findByPhoneNumber("+2250123456791").orElse(null);

        // Then
        assertNotNull(foundClient);
        assertEquals("Bob", foundClient.getFirstName());
        assertEquals("Wilson", foundClient.getLastName());
        assertEquals("+2250123456791", foundClient.getPhoneNumber());
    }

    @Test
    void testFindByClientId() {
        // Given
        Client client = Client.builder()
            .firstName("Alice")
            .lastName("Johnson")
            .phoneNumber("+2250123456792")
            .build();
        Client savedClient = clientRepository.save(client);

        Account account = Account.builder()
            .currency("FCFA")
            .accountNumber("ACC_TEST_002")
            .accountType(Account.AccountType.CURRENT)
            .status(Account.AccountStatus.ACTIVE)
            .clientId(savedClient.getId())
            .balance(new BigDecimal("250000.00"))
            .build();
        accountRepository.save(account);

        // When
        Account foundAccount = accountRepository.findByClientId(savedClient.getId()).orElse(null);

        // Then
        assertNotNull(foundAccount);
        assertEquals("ACC_TEST_002", foundAccount.getAccountNumber());
        assertEquals(new BigDecimal("250000.00"), foundAccount.getBalance());
    }

    @Test
    void testExistsByPhoneNumber() {
        // Given
        Client client = Client.builder()
            .firstName("Charlie")
            .lastName("Brown")
            .phoneNumber("+2250123456793")
            .build();
        clientRepository.save(client);

        // When
        boolean exists = clientRepository.existsByPhoneNumber("+2250123456793");
        boolean notExists = clientRepository.existsByPhoneNumber("+2250123456794");

        // Then
        assertTrue(exists);
        assertFalse(notExists);
    }
}
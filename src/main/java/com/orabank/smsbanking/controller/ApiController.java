package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import com.orabank.smsbanking.repository.SmsLogRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiController {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;
    private final SmsLogRepository smsLogRepository;

    /**
     * Récupère tous les comptes bancaires avec les informations client.
     * Endpoint protégé nécessitant une authentification.
     */
    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        log.info("Récupération de la liste des comptes");
        List<Account> accounts = accountRepository.findAll();

        // Récupérer tous les clients pour enrichir les comptes
        Map<Long, Client> clientMap = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, c -> c));

        List<AccountDTO> accountDTOs = accounts.stream()
                .map(account -> {
                    Client client = clientMap.get(account.getClientId());
                    return convertToAccountDTO(account, client);
                })
                .collect(Collectors.toList());

        log.info("Comptes retournés: {} (dont {} comptes système)",
                accountDTOs.size(),
                accountDTOs.stream().filter(a -> a.getClientId() == null).count());

        return ResponseEntity.ok(accountDTOs);
    }

    /**
     * Récupère tous les clients.
     */
    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<Client>> getAllClients() {
        log.info("Récupération de la liste des clients");
        return ResponseEntity.ok(clientRepository.findAll());
    }

   
    /**
     * Récupère toutes les transactions avec les informations client.
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<TransactionDTO>> getAllTransactions() {
        log.info("Récupération de la liste des transactions");
        List<Transaction> transactions = transactionRepository.findAll();

        Map<Long, Account> accountMap = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, a -> a));
        Map<Long, Client> clientMap = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, c -> c));

        List<TransactionDTO> transactionDTOs = transactions.stream()
                .map(transaction -> {
                    Account account = accountMap.get(transaction.getAccountId());
                    Client client = account != null ? clientMap.get(account.getClientId()) : null;
                    return convertToTransactionDTO(transaction, account, client);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(transactionDTOs);
    }

    /**
     * Récupère tous les logs SMS.
     */
    @GetMapping("/sms/logs")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SmsLog>> getAllSmsLogs() {
        log.info("Récupération de la liste des logs SMS");
        return ResponseEntity.ok(smsLogRepository.findAll());
    }

    private AccountDTO convertToAccountDTO(Account account, Client client) {
        AccountDTO dto = new AccountDTO();
        dto.setId(account.getId());
        dto.setAccountNumber(account.getAccountNumber());
        dto.setClientId(account.getClientId());
        dto.setBalance(account.getBalance());
        dto.setCurrency(account.getCurrency());
        dto.setActive(account.isActive());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        dto.setType(account.getAccountType().name());
        dto.setStatus(account.getStatus().name());
        dto.setSystemAccount(account.isSystemAccount());
        dto.setDescription(account.getDescription());
        dto.setFeeType(account.getFeeType());

        if (client != null) {
            dto.setClientName(client.getFirstName() + " " + client.getLastName());
            dto.setClientPhoneNumber(client.getPhoneNumber());
        } else {
            dto.setClientName("SYSTÈME");
            dto.setClientPhoneNumber("N/A");
        }

        return dto;
    }

    private TransactionDTO convertToTransactionDTO(Transaction transaction, Account account, Client client) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(transaction.getId());
        dto.setTransactionId(transaction.getTransactionId());
        dto.setAccountId(transaction.getAccountId());
        dto.setRelatedAccountId(transaction.getRelatedAccountId());
        dto.setAmount(transaction.getAmount());
        dto.setCurrency(transaction.getCurrency());
        dto.setType(transaction.getType());
        dto.setDescription(transaction.getDescription());
        dto.setReference(transaction.getReference());
        dto.setStatus(transaction.getStatus().name());
        dto.setTimestamp(transaction.getTimestamp());
        dto.setCompletedAt(transaction.getCompletedAt());
        dto.setCreatedAt(transaction.getCreatedAt());

        if (account != null) {
            dto.setAccountNumber(account.getAccountNumber());
        }

        if (client != null) {
            dto.setClientName(client.getFirstName() + " " + client.getLastName());
            dto.setClientPhoneNumber(client.getPhoneNumber());
        }

        return dto;
    }

    @Data
    public static class AccountDTO {
        private Long id;
        private String accountNumber;
        private Long clientId;
        private String clientName;
        private String clientPhoneNumber;
        private BigDecimal balance;
        private String currency;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String type;
        private String status;
        private boolean systemAccount;
        private String description;
        private String feeType;
    }

    @Data
    public static class TransactionDTO {
        private Long id;
        private String transactionId;
        private Long accountId;
        private Long relatedAccountId;
        private BigDecimal amount;
        private String currency;
        private String type;
        private String description;
        private String reference;
        private String status;
        private LocalDateTime timestamp;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private String accountNumber;
        private String clientName;
        private String clientPhoneNumber;
    }
}
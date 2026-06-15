package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.SmsLog;
import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.SmsDirection;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.SmsLogRepository;
import com.orabank.smsbanking.repository.TransactionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;
    private final SmsLogRepository smsLogRepository;

    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        log.info("Récupération de la liste des clients pour le dashboard");
        List<Client> clients = clientRepository.findAll();
        List<ClientDTO> clientDTOs = clients.stream()
                .map(this::convertToClientDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(clientDTOs);
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')") // SEUL L'ADMIN PEUT VOIR LES COMPTES
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        log.info("Récupération de la liste des comptes pour le dashboard");
        List<Account> accounts = accountRepository.findAll();
        Map<Long, Client> clientMap = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, c -> c));

        List<AccountDTO> accountDTOs = accounts.stream()
                .map(account -> convertToAccountDTO(account, clientMap.get(account.getClientId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(accountDTOs);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<TransactionDTO>> getAllTransactions() {
        log.info("Récupération de la liste des transactions pour le dashboard");
        List<Transaction> transactions = transactionRepository.findAll();

        Map<Long, Client> clientMap = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, c -> c));
        Map<Long, Account> accountMap = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, a -> a));

        List<TransactionDTO> transactionDTOs = transactions.stream()
                .map(transaction -> {
                    Account account = accountMap.get(transaction.getAccountId());
                    Client client = account != null ? clientMap.get(account.getClientId()) : null;
                    return convertToTransactionDTO(transaction, account, client);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(transactionDTOs);
    }

    @GetMapping("/sms/logs")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SmsLogDTO>> getAllSmsLogs(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String phoneNumber) {
        log.info("Récupération de la liste des logs SMS pour le dashboard");
        List<SmsLog> smsLogs;

        if (direction != null && phoneNumber != null) {
            smsLogs = smsLogRepository.findByDirectionAndToOrSender(
                    SmsDirection.valueOf(direction.toUpperCase()), phoneNumber, phoneNumber);
        } else if (direction != null) {
            smsLogs = smsLogRepository.findByDirection(SmsDirection.valueOf(direction.toUpperCase()));
        } else if (phoneNumber != null) {
            smsLogs = smsLogRepository.findByToOrSender(phoneNumber, phoneNumber);
        } else {
            smsLogs = smsLogRepository.findAll();
        }

        List<SmsLogDTO> smsLogDTOs = smsLogs.stream()
                .map(this::convertToSmsLogDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(smsLogDTOs);
    }

    private ClientDTO convertToClientDTO(Client client) {
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setName(client.getFirstName() + " " + client.getLastName());
        dto.setFirstName(client.getFirstName());
        dto.setLastName(client.getLastName());
        dto.setPhoneNumber(client.getPhoneNumber());
        dto.setPhone(client.getPhoneNumber());
        dto.setEmail(client.getEmail());
        dto.setActive(client.isActive());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setUpdatedAt(client.getUpdatedAt());
        return dto;
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
        if (client != null) {
            dto.setClientName(client.getFirstName() + " " + client.getLastName());
            dto.setClientPhoneNumber(client.getPhoneNumber());
        }
        return dto;
    }

    private TransactionDTO convertToTransactionDTO(Transaction transaction, Account account, Client client) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(transaction.getId());
        dto.setTransactionId(transaction.getTransactionId());
        dto.setAccountId(transaction.getAccountId());
        dto.setRelatedAccountId(transaction.getRelatedAccountId());

        // Le montant en base est déjà le montant du transfert (ex: 50000), pas le total débité.
        // Le frontend masquera cette colonne pour les utilisateurs non-ADMIN.
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

    private SmsLogDTO convertToSmsLogDTO(SmsLog smsLog) {
        SmsLogDTO dto = new SmsLogDTO();
        dto.setId(smsLog.getId());
        dto.setSender(smsLog.getSender());
        dto.setTo(smsLog.getTo());
        dto.setBody(smsLog.getBody());
        dto.setMessage(smsLog.getBody());
        dto.setDirection(smsLog.getDirection().name());
        dto.setType(smsLog.getDirection().name());
        dto.setRelatedSmsId(smsLog.getRelatedSmsId());
        dto.setErrorMessage(smsLog.getErrorMessage());
        dto.setTimestamp(smsLog.getTimestamp());
        dto.setProcessedSuccessfully(smsLog.getProcessedSuccessfully());
        dto.setCreatedAt(smsLog.getCreatedAt());

        if (smsLog.getProcessedSuccessfully() != null && !smsLog.getProcessedSuccessfully()) {
            dto.setStatus("FAILED");
        } else if (smsLog.getDirection() == SmsDirection.INCOMING) {
            dto.setStatus("RECEIVED");
        } else {
            dto.setStatus("SENT");
        }

        if (smsLog.getDirection() == SmsDirection.OUTGOING) {
            dto.setPhoneNumber(smsLog.getTo());
        } else {
            dto.setPhoneNumber(smsLog.getSender());
        }
        return dto;
    }

    @Data
    public static class ClientDTO {
        private Long id;
        private String name;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String phone;
        private String email;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class AccountDTO {
        private Long id;
        private String accountNumber;
        private Long clientId;
        private String clientName;
        private String clientPhoneNumber;
        private java.math.BigDecimal balance;
        private String currency;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String type;
        private String status;
    }

    @Data
    public static class TransactionDTO {
        private Long id;
        private String transactionId;
        private Long accountId;
        private Long relatedAccountId;
        private java.math.BigDecimal amount;
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

    @Data
    public static class SmsLogDTO {
        private Long id;
        private String sender;
        private String to;
        private String body;
        private String message;
        private String phoneNumber;
        private String direction;
        private String type;
        private String status;
        private Long relatedSmsId;
        private String errorMessage;
        private LocalDateTime timestamp;
        private Boolean processedSuccessfully;
        private LocalDateTime createdAt;
    }
}
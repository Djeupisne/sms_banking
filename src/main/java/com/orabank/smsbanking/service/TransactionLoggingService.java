package com.orabank.smsbanking.service;

import com.orabank.smsbanking.entity.TransactionLog;
import com.orabank.smsbanking.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLoggingService {

    private final TransactionLogRepository logRepository;

    @Transactional
    public void logTransaction(String action, String transactionType, Double amount,
                               String sourceAccount, String targetAccount,
                               String sourcePhone, String targetPhone,
                               String description, String transactionReference,
                               String status, String errorMessage,
                               Double feesAmount, Double totalAmount,
                               HttpServletRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        String userRole = auth != null ? auth.getAuthorities().toString() : "NONE";

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        TransactionLog logEntry = TransactionLog.builder()
                .username(username)
                .userRole(userRole)
                .action(action)
                .transactionType(transactionType)
                .amount(amount)
                .sourceAccount(sourceAccount)
                .targetAccount(targetAccount)
                .sourcePhone(sourcePhone)
                .targetPhone(targetPhone)
                .description(description)
                .transactionReference(transactionReference)
                .status(status)
                .errorMessage(errorMessage)
                .ipAddress(ipAddress)
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .feesAmount(feesAmount)
                .totalAmount(totalAmount)
                .build();

        logRepository.save(logEntry);

        if ("FAILED".equals(status)) {
            log.error("❌ Transaction échouée - User: {}, Type: {}, Amount: {}", username, transactionType, amount);
        } else {
            log.info("✅ Transaction loggée - User: {}, Type: {}, Amount: {}", username, transactionType, amount);
        }
    }

    public Page<TransactionLog> getAllLogs(Pageable pageable) {
        return logRepository.findAll(pageable);
    }

    public Page<TransactionLog> getUserLogs(String username, Pageable pageable) {
        return logRepository.findByUsername(username, pageable);
    }

    public List<Map<String, Object>> getUserStats() {
        List<Object[]> results = logRepository.countTransactionsByUser();
        return results.stream().map(result -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("username", result[0]);
            stats.put("transactionCount", result[1]);
            return stats;
        }).toList();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
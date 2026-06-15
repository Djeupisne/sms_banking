package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.entity.TransactionLog;
import com.orabank.smsbanking.service.TransactionLoggingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/transaction-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TransactionLogController {

    private final TransactionLoggingService loggingService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransactionLog> logs = loggingService.getAllLogs(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Map<String, Object> response = new HashMap<>();
        response.put("logs", logs.getContent());
        response.put("totalPages", logs.getTotalPages());
        response.put("totalElements", logs.getTotalElements());
        response.put("currentPage", logs.getNumber());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<Map<String, Object>> getUserLogs(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransactionLog> logs = loggingService.getUserLogs(username,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Map<String, Object> response = new HashMap<>();
        response.put("logs", logs.getContent());
        response.put("username", username);
        response.put("totalElements", logs.getTotalElements());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics/users")
    public ResponseEntity<List<Map<String, Object>>> getUserStatistics() {
        return ResponseEntity.ok(loggingService.getUserStats());
    }
}
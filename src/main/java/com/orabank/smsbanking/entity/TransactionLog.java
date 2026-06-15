package com.orabank.smsbanking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String userRole;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private String sourceAccount;
    private String targetAccount;
    private String sourcePhone;
    private String targetPhone;

    @Column(length = 500)
    private String description;

    private String transactionReference;

    @Column(nullable = false)
    private String status;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false, length = 1000)
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(precision = 15, scale = 2)
    private BigDecimal feesAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAmount;
}
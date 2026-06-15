package com.orabank.smsbanking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")  // ✅ Plus d'index ici
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

    @Column(nullable = false)
    private Double amount;

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
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Double feesAmount;
    private Double totalAmount;
}
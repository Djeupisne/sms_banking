package com.orabank.smsbanking.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = true)  // CHANGÉ: nullable = true pour permettre les comptes système
    private Long clientId;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "FCFA";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountType accountType = AccountType.CURRENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    // NOUVEAUX CHAMPS POUR LES COMPTES SYSTÈME
    @Column(name = "is_system_account")
    @Builder.Default
    private boolean systemAccount = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "fee_type", length = 50)
    private String feeType;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        // Générer un numéro de compte pour les comptes système si non fourni
        if (accountNumber == null && systemAccount) {
            accountNumber = "SYS_" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccountType {
        CURRENT,
        SAVINGS,
        CHECKING,
        FEE,
        FEE_MOBILE_MONEY,
        FEE_INTERNAL_TRANSFER,
        SYSTEM
    }

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        CLOSED
    }

    // MÉTHODES UTILITAIRES
    public boolean isSystemAccount() {
        return systemAccount;
    }

    public boolean isFeesAccount() {
        return accountType == AccountType.FEE ||
                accountType == AccountType.FEE_MOBILE_MONEY ||
                accountType == AccountType.FEE_INTERNAL_TRANSFER ||
                systemAccount;
    }
}
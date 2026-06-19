package com.orabank.smsbanking.service.saga;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MobileMoneySagaContext {

    private String sagaId;
    private String phoneNumber;
    private BigDecimal amount;
    private BigDecimal fees;
    private BigDecimal totalAmount;
    private SagaState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String errorMessage;
    private String mobileMoneyTransactionId;
    @Builder.Default
    private int compensationAttempts = 0;
    private Long feesAccountId;
    private BigDecimal feesPercentage;
    private BigDecimal feesMin;
    private BigDecimal feesMax;
    private String debitTransactionId;

    // ✅ NOUVEAU : Stocker le compte utilisé (pour logs et traçabilité)
    private String usedAccountNumber;
    private Long usedAccountId;

    // Montant maximum autorisé pour un transfert
    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("500000");

    // Taux de frais : 10%
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("10");

    public static MobileMoneySagaContext create(String phoneNumber, BigDecimal amount) {
        // Vérifier la limite maximale
        if (amount.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            throw new IllegalArgumentException("Le montant maximum autorisé est de 500 000 FCFA");
        }

        return MobileMoneySagaContext.builder()
                .sagaId(UUID.randomUUID().toString())
                .phoneNumber(phoneNumber)
                .amount(amount)
                .state(SagaState.INITIATED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .compensationAttempts(0)
                .build();
    }

    /**
     * Calcule les frais à 10% du montant envoyé
     * Frais = montant × 10%
     */
    public void calculateFees(BigDecimal feesPercentage, BigDecimal feesMin, BigDecimal feesMax) {
        // Frais = 10% du montant
        BigDecimal percentageDivisor = new BigDecimal("100");
        BigDecimal calculatedFees = this.amount
                .multiply(FEE_PERCENTAGE)
                .divide(percentageDivisor, 0, RoundingMode.CEILING);

        this.fees = calculatedFees;
        this.totalAmount = this.amount.add(this.fees);
        this.feesPercentage = FEE_PERCENTAGE;
        this.feesMin = BigDecimal.ZERO;
        this.feesMax = new BigDecimal("50000");
    }

    public void calculateFees() {
        calculateFees(null, null, null);
    }

    public boolean isValidAmount() {
        return this.amount != null &&
                this.amount.compareTo(BigDecimal.ZERO) > 0 &&
                this.amount.compareTo(MAX_TRANSFER_AMOUNT) <= 0;
    }

    public void fail(String message) {
        this.state = SagaState.COMPENSATION_FAILED;
        this.errorMessage = message;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        this.state = SagaState.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }
}
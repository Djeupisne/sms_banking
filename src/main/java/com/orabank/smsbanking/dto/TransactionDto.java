package com.orabank.smsbanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {
    private Long id;
    private String transactionId;
    private String type;
    private String description;
    private String reference;
    private String status;
    private LocalDateTime createdAt;

    // Ces champs seront masqués pour le rôle USER
    private BigDecimal amount;
    private BigDecimal fees;
    private BigDecimal totalAmount;
    private String accountNumber;
}
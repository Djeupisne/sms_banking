package com.orabank.smsbanking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO pour les virements internes entre comptes.
 */
@Data
public class InternalTransferRequest {
    @NotBlank(message = "Le numéro de compte source est requis")
    private String sourceAccountNumber;

    @NotBlank(message = "Le numéro de compte cible est requis")
    private String targetAccountNumber;

    @NotNull(message = "Le montant est requis")
    @Positive(message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;

    private String description;
}

package com.orabank.smsbanking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO pour les opérations de crédit/débit manuel sur un compte.
 */
@Data
public class AccountOperationRequest {
    @NotBlank(message = "Le numéro de compte est requis")
    private String accountNumber;

    @NotNull(message = "Le montant est requis")
    @Positive(message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;

    private String description;
}

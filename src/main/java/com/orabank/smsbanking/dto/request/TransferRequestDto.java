package com.orabank.smsbanking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequestDto {
    @NotBlank(message = "Le numéro de compte est requis")
    private String accountNumber;

    @NotNull(message = "Le montant est requis")
    @Positive(message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;

    private String recipientPhone;
}

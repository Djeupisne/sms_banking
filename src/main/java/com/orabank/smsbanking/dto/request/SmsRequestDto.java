package com.orabank.smsbanking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsRequestDto {
    @NotBlank(message = "Le numéro d'expéditeur est requis")
    private String from;

    @NotBlank(message = "Le numéro de destination est requis")
    private String to;

    @NotBlank(message = "Le corps du message est requis")
    @Size(max = 1600, message = "Le message ne peut pas dépasser 1600 caractères")
    private String body;
    
    /**
     * Timestamp de la requête (en millisecondes depuis epoch).
     * Utilisé pour la validation de la signature HMAC et la prévention des attaques par replay.
     */
    private Long timestamp;
}

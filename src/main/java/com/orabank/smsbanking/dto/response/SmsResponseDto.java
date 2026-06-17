package com.orabank.smsbanking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsResponseDto {
    private String to;
    private String message;
    private String reference;
    private String status;

    public SmsResponseDto(String message) {
        this.message = message;
    }

    public SmsResponseDto(String to, String message) {
        this.to = to;
        this.message = message;
    }

    public SmsResponseDto(String to, String message, String reference) {
        this.to = to;
        this.message = message;
        this.reference = reference;
    }
}
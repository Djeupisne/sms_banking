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
    
    public SmsResponseDto(String message) {
        this.message = message;
    }
}

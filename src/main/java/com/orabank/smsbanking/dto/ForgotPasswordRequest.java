package com.orabank.smsbanking.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
}
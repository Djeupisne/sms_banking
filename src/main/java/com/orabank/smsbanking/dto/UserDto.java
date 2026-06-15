package com.orabank.smsbanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private boolean active;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}
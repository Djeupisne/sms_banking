package com.orabank.smsbanking.entity;

import com.orabank.smsbanking.entity.enums.SmsDirection;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "sms_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "sender")
    private String sender;

    @Column(name = "\"to\"", nullable = false)  // ← Échappement du mot clé SQL
    private String to;

    @Column(nullable = false, length = 1600)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsDirection direction;

    private Long relatedSmsId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "processed_successfully")
    @Builder.Default
    private Boolean processedSuccessfully = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (processedSuccessfully == null) {
            processedSuccessfully = true;
        }
    }
}
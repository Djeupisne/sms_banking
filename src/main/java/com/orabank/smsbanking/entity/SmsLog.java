package com.orabank.smsbanking.entity;

import com.orabank.smsbanking.entity.enums.SmsDirection;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

    @Column(name = "\"to\"", nullable = false)
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

    @Column(name = "reference", unique = true)
    private String reference;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (processedSuccessfully == null) {
            processedSuccessfully = true;
        }
        // La référence est générée uniquement si elle est nulle ou vide
        // Pour éviter les doublons lors des retries Resilience4j
        // IMPORTANT: Ne pas régénérer si déjà définie par le service
        if (reference == null || reference.trim().isEmpty()) {
            reference = generateReference();
        }
    }

    /**
     * Génère une référence unique avec UUID complet pour éviter les collisions.
     * Format: SMS_yyyyMMdd_HHmmss_XXXX où XXXX est un UUID court (8 caractères)
     * 
     * @return une référence unique
     */
    public static String generateReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        // Utiliser 8 caractères d'UUID pour une unicité quasi-certaine
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "SMS_" + timestamp + "_" + uuid;
    }
}
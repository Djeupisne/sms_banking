package com.orabank.smsbanking.entity;

import com.orabank.smsbanking.entity.enums.SmsDirection;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    //  NOUVEAU : Référence unique
    @Column(name = "reference", unique = true)
    private String reference;

    //  NOUVEAU : ID de conversation (pour lier les messages)
    @Column(name = "conversation_id")
    private String conversationId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (processedSuccessfully == null) {
            processedSuccessfully = true;
        }
        //  Générer la référence si elle n'existe pas
        if (reference == null || reference.isEmpty()) {
            reference = generateReference();
        }
        //  Générer l'ID de conversation si elle n'existe pas
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = generateConversationId();
        }
    }

    /**
     * Génère une référence unique pour le SMS
     * Format: SMS_20260617_142530_1234
     */
    private String generateReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String random = String.format("%04d", (int)(Math.random() * 10000));
        return "SMS_" + timestamp + "_" + random;
    }

    /**
     * Génère un ID de conversation basé sur le timestamp
     * Format: CONV_20260617_142530
     */
    private String generateConversationId() {
        return "CONV_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
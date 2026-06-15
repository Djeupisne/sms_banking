package com.orabank.smsbanking.service;

import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.entity.PasswordResetToken;
import com.orabank.smsbanking.repository.ClientRepository;
import com.orabank.smsbanking.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final ClientRepository clientRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.url:http://localhost:3001}")
    private String frontendUrl;

    @Value("${jwt.reset-password.expiration-ms:1800000}")
    private long tokenExpirationMs;

    @Transactional
    public boolean sendResetPasswordEmail(String email) {
        Optional<Client> clientOpt = clientRepository.findByEmail(email);
        if (clientOpt.isEmpty()) {
            log.warn("Reset password requested for non-existent email: {}", maskEmail(email));
            return true;
        }

        Client client = clientOpt.get();

        tokenRepository.findByEmailAndUsedFalse(email).ifPresent(tokenRepository::delete);

        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(tokenExpirationMs / 1000);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .email(email)
                .expiryDate(expiryDate)
                .used(false)
                .build();
        tokenRepository.save(resetToken);

        String name = client.getFirstName() + " " + client.getLastName();

        boolean sent = emailService.sendResetPasswordEmail(email, name, token);

        if (sent) {
            log.info("Reset password email sent to: {}", maskEmail(email));
        } else {
            log.error("Failed to send reset password email to: {}", maskEmail(email));
        }

        return sent;
    }

    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            log.warn("Invalid reset token: token not found");
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.isUsed()) {
            log.warn("Invalid reset token: token already used");
            return false;
        }

        if (resetToken.isExpired()) {
            log.warn("Invalid reset token: token expired");
            return false;
        }

        return true;
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            log.warn("Reset password failed: token not found");
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.isUsed()) {
            log.warn("Reset password failed: token already used");
            return false;
        }

        if (resetToken.isExpired()) {
            log.warn("Reset password failed: token expired");
            return false;
        }

        Optional<Client> clientOpt = clientRepository.findByEmail(resetToken.getEmail());
        if (clientOpt.isEmpty()) {
            log.warn("Reset password failed: client not found for email: {}", maskEmail(resetToken.getEmail()));
            return false;
        }

        Client client = clientOpt.get();

        client.setPassword(passwordEncoder.encode(newPassword));
        client.setPasswordUpdatedAt(LocalDateTime.now());
        clientRepository.save(client);

        tokenRepository.markAsUsed(token);

        log.info("Password reset successfully for client: {}", client.getPhoneNumber());
        return true;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String localPart = parts[0];
        if (localPart.length() <= 2) return "***@" + parts[1];
        return localPart.substring(0, 2) + "***@" + parts[1];
    }
}
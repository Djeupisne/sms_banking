package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.dto.ForgotPasswordRequest;
import com.orabank.smsbanking.dto.ResetPasswordRequest;
import com.orabank.smsbanking.entity.PasswordResetToken;
import com.orabank.smsbanking.entity.User;
import com.orabank.smsbanking.repository.PasswordResetTokenRepository;
import com.orabank.smsbanking.repository.UserRepository;
import com.orabank.smsbanking.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuthInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            log.debug("Utilisateur non authentifié");
            return ResponseEntity.status(401).build();
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        boolean isAdmin = roles.contains("ROLE_ADMIN");

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("username", authentication.getName());
        response.put("roles", roles);
        response.put("isAdmin", isAdmin);

        log.debug("Authentification réussie pour l'utilisateur: {}, Rôles: {}", authentication.getName(), roles);
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // ENDPOINTS DE RÉINITIALISATION DE MOT DE PASSE
    // ============================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        log.info("Demande de réinitialisation de mot de passe pour: {}", request.getEmail());

        Map<String, Object> response = new HashMap<>();

        try {
            // Vérifier si l'email existe
            User user = userRepository.findByEmail(request.getEmail()).orElse(null);

            if (user == null) {
                // Pour des raisons de sécurité, on ne révèle pas si l'email existe ou non
                response.put("success", true);
                response.put("message", "Si un compte existe avec cet email, vous recevrez un lien de réinitialisation.");
                return ResponseEntity.ok(response);
            }

            // Supprimer les anciens tokens expirés
            tokenRepository.deleteAllExpiredTokens(LocalDateTime.now());

            // Créer un nouveau token
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .email(request.getEmail())
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();

            tokenRepository.save(resetToken);

            // Envoyer l'email avec le nom de l'utilisateur
            String userName = user.getFullName() != null ? user.getFullName().split(" ")[0] : "Cher client";
            boolean emailSent = emailService.sendResetPasswordEmail(request.getEmail(), userName, token);

            if (emailSent) {
                response.put("success", true);
                response.put("message", "Un email de réinitialisation a été envoyé à votre adresse.");
            } else {
                response.put("success", false);
                response.put("message", "Une erreur est survenue lors de l'envoi de l'email. Veuillez réessayer.");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la demande de réinitialisation", e);
            response.put("success", false);
            response.put("message", "Une erreur est survenue. Veuillez réessayer.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<Map<String, Object>> validateResetToken(@RequestParam String token) {
        log.info("Validation du token: {}", token);

        Map<String, Object> response = new HashMap<>();

        try {
            PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);

            if (resetToken == null) {
                response.put("valid", false);
                response.put("message", "Token invalide.");
                return ResponseEntity.badRequest().body(response);
            }

            if (resetToken.isUsed()) {
                response.put("valid", false);
                response.put("message", "Ce lien a déjà été utilisé.");
                return ResponseEntity.badRequest().body(response);
            }

            if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                response.put("valid", false);
                response.put("message", "Ce lien a expiré. Veuillez refaire une demande.");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("valid", true);
            response.put("email", resetToken.getEmail());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la validation du token", e);
            response.put("valid", false);
            response.put("message", "Erreur lors de la validation.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody ResetPasswordRequest request) {
        log.info("Demande de réinitialisation du mot de passe");

        Map<String, Object> response = new HashMap<>();

        try {
            PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken()).orElse(null);

            if (resetToken == null) {
                response.put("success", false);
                response.put("message", "Token invalide.");
                return ResponseEntity.badRequest().body(response);
            }

            if (resetToken.isUsed()) {
                response.put("success", false);
                response.put("message", "Ce lien a déjà été utilisé.");
                return ResponseEntity.badRequest().body(response);
            }

            if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                response.put("success", false);
                response.put("message", "Ce lien a expiré. Veuillez refaire une demande.");
                return ResponseEntity.badRequest().body(response);
            }

            // Vérifier si l'utilisateur existe
            User user = userRepository.findByEmail(resetToken.getEmail()).orElse(null);

            if (user == null) {
                response.put("success", false);
                response.put("message", "Utilisateur non trouvé.");
                return ResponseEntity.badRequest().body(response);
            }

            // Vérifier la validité du nouveau mot de passe
            String newPassword = request.getNewPassword();
            if (newPassword == null || newPassword.length() < 6) {
                response.put("success", false);
                response.put("message", "Le mot de passe doit contenir au moins 6 caractères.");
                return ResponseEntity.badRequest().body(response);
            }

            // Mettre à jour le mot de passe
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            // Marquer le token comme utilisé
            tokenRepository.markAsUsed(request.getToken());

            response.put("success", true);
            response.put("message", "Votre mot de passe a été réinitialisé avec succès.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la réinitialisation du mot de passe", e);
            response.put("success", false);
            response.put("message", "Une erreur est survenue. Veuillez réessayer.");
            return ResponseEntity.badRequest().body(response);
        }
    }
}
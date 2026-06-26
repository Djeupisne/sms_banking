package com.orabank.smsbanking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${jwt.secret:}") String secretKey,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            @Value("${spring.profiles.active:default}") String activeProfile) {

        // VÉRIFICATION ROBUSTE : Null, vide, OU TROP COURTE (< 32 caractères / 256 bits)
        if (secretKey == null || secretKey.trim().isEmpty() || secretKey.length() < 32) {

            if ("dev".equals(activeProfile) || "default".equals(activeProfile) || "test".equals(activeProfile)) {
                // TOLÉRANCE EN DEV : Génération automatique pour ne pas bloquer le développeur
                log.warn(" [DEV] Clé JWT absente ou trop courte ({} caractères). Génération automatique d'une clé aléatoire temporaire.",
                        secretKey != null ? secretKey.length() : 0);
                secretKey = generateSecureKey();
            } else {
                // SÉCURITÉ EN PRODUCTION : Fail-Fast (Arrêt immédiat de l'application)
                String errorMsg = String.format(
                        " ERREUR CRITIQUE DE SÉCURITÉ : La variable d'environnement JWT_SECRET est absente ou trop courte (%d caractères). " +
                                "En production, une clé robuste d'au moins 32 caractères est OBLIGATOIRE. Arrêt de l'application.",
                        secretKey != null ? secretKey.length() : 0
                );
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        }

        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;

        log.info(" JWT Token Service initialized [Profile: {}]. Clé: {} octets, Expiration: {} ms",
                activeProfile, this.secretKey.getEncoded().length, expirationMs);
    }

    private String generateSecureKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[64]; // 64 octets = 512 bits
        secureRandom.nextBytes(randomBytes);
        String generatedKey = Base64.getEncoder().encodeToString(randomBytes);
        log.info("Clé JWT générée automatiquement: {} caractères", generatedKey.length());
        return generatedKey;
    }

    public String generateWebhookToken(String from, String to, String body) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiration = new Date(now + expirationMs);

        String bodyHash = sha256Hash(body);

        Map<String, Object> claims = new HashMap<>();
        claims.put("from", from);
        claims.put("to", to);
        claims.put("bodyHash", bodyHash);
        claims.put("iat", now / 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("sms-webhook")
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.debug("Token JWT invalide: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractFrom(String token) {
        return extractClaim(token, "from");
    }

    public String extractTo(String token) {
        return extractClaim(token, "to");
    }

    public String extractBodyHash(String token) {
        return extractClaim(token, "bodyHash");
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, "exp", Date.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateTokenAgainstRequest(String token, String from, String to, String body) {
        try {
            if (!validateToken(token)) {
                log.warn("Token JWT invalide ou expiré");
                return false;
            }

            Claims claims = extractClaims(token);

            String tokenFrom = claims.get("from", String.class);
            String tokenTo = claims.get("to", String.class);
            String tokenBodyHash = claims.get("bodyHash", String.class);

            if (!from.equals(tokenFrom)) {
                log.warn("Mismatch 'from': request={}, token={}", from, tokenFrom);
                return false;
            }

            if (!to.equals(tokenTo)) {
                log.warn("Mismatch 'to': request={}, token={}", to, tokenTo);
                return false;
            }

            String requestBodyHash = sha256Hash(body);
            if (!requestBodyHash.equals(tokenBodyHash)) {
                log.warn("Mismatch 'bodyHash': request={}, token={}", requestBodyHash, tokenBodyHash);
                return false;
            }

            log.debug("Token JWT validé avec succès pour from={}, to={}", from, to);
            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la validation du token JWT", e);
            return false;
        }
    }

    private <T> T extractClaim(String token, String claimName, Class<T> type) {
        final Claims claims = extractClaims(token);
        return claims.get(claimName, type);
    }

    private String extractClaim(String token, String claimName) {
        return extractClaim(token, claimName, String.class);
    }

    private String sha256Hash(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, Math.min(16, hexString.length()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
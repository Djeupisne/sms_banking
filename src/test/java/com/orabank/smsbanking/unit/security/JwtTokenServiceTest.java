package com.orabank.smsbanking.unit.security;

import com.orabank.smsbanking.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour JwtTokenService.
 * Vérifie la génération et validation des tokens JWT pour les webhooks SMS.
 */
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    // Clé de test (doit faire au moins 32 caractères pour HS256)
    private static final String TEST_JWT_SECRET = "test_jwt_secret_key_for_unit_tests_min_32_chars";
    private static final long TEST_EXPIRATION_MS = 3600000; // 1 heure

    @BeforeEach
    void setUp() {
        // Création manuelle du service pour les tests
        // ✅ CORRECTION : Ajout du 3ème paramètre "test" pour le profil actif
        jwtTokenService = new JwtTokenService(TEST_JWT_SECRET, TEST_EXPIRATION_MS, "test");
    }

    @Test
    @DisplayName("Génération de token JWT avec succès")
    void testGenerateWebhookToken_Success() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";

        // When
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // Then
        assertNotNull(token);
        assertTrue(token.length() > 0);

        // Un token JWT a 3 parties séparées par des points
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        System.out.println("Token généré: " + token);
    }

    @Test
    @DisplayName("Validation d'un token JWT valide")
    void testValidateToken_ValidToken() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When
        boolean isValid = jwtTokenService.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Échec de validation avec un token invalide")
    void testValidateToken_InvalidToken() {
        // Given
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature";

        // When
        boolean isValid = jwtTokenService.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Échec de validation avec un token vide")
    void testValidateToken_EmptyToken() {
        // When
        boolean isValid = jwtTokenService.validateToken("");

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Extraction des claims depuis un token valide")
    void testExtractClaims_Success() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When
        var claims = jwtTokenService.extractClaims(token);

        // Then
        assertNotNull(claims);
        assertEquals(from, claims.get("from"));
        assertEquals(to, claims.get("to"));
        assertNotNull(claims.get("bodyHash"));
        assertEquals("sms-webhook", claims.getSubject());
    }

    @Test
    @DisplayName("Validation du token contre les données de requête - Succès")
    void testValidateTokenAgainstRequest_Success() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When
        boolean isValid = jwtTokenService.validateTokenAgainstRequest(token, from, to, body);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Échec de validation - mismatch sur 'from'")
    void testValidateTokenAgainstRequest_MismatchFrom() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When - on change le 'from'
        boolean isValid = jwtTokenService.validateTokenAgainstRequest(token, "+22899999999", to, body);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Échec de validation - mismatch sur 'to'")
    void testValidateTokenAgainstRequest_MismatchTo() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When - on change le 'to'
        boolean isValid = jwtTokenService.validateTokenAgainstRequest(token, from, "AUTRE", body);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Échec de validation - mismatch sur 'body'")
    void testValidateTokenAgainstRequest_MismatchBody() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When - on change le 'body'
        boolean isValid = jwtTokenService.validateTokenAgainstRequest(token, from, to, "TRANSFERT");

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Le token contient une date d'expiration valide")
    void testExtractExpiration() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When
        var expiration = jwtTokenService.extractExpiration(token);

        // Then
        assertNotNull(expiration);

        // L'expiration doit être dans le futur (environ 1 heure)
        long now = System.currentTimeMillis();
        long expTime = expiration.getTime();
        assertTrue(expTime > now);

        // L'expiration ne doit pas être trop loin (moins de 2 heures)
        assertTrue(expTime < now + (2 * 3600000));
    }

    @Test
    @DisplayName("Un token non expiré est considéré comme valide")
    void testIsTokenExpired_NotExpired() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";
        String token = jwtTokenService.generateWebhookToken(from, to, body);

        // When
        boolean isExpired = jwtTokenService.isTokenExpired(token);

        // Then
        assertFalse(isExpired);
    }

    @Test
    @DisplayName("Les tokens générés sont différents à chaque appel")
    void testGenerateToken_UniqueTokens() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";

        // When - générer deux tokens avec un délai suffisant (1 seconde)
        String token1 = jwtTokenService.generateWebhookToken(from, to, body);

        try {
            Thread.sleep(1100); // Attendre >1s pour que le timestamp en secondes change
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String token2 = jwtTokenService.generateWebhookToken(from, to, body);

        // Then - les tokens doivent être différents (car iat est différent)
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Le bodyHash est cohérent pour le même body")
    void testBodyHash_Consistent() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";
        String body = "SOLDE";

        // When - générer deux tokens avec le même body
        String token1 = jwtTokenService.generateWebhookToken(from, to, body);
        String token2 = jwtTokenService.generateWebhookToken(from, to, body);

        // Then - extraire les bodyHash
        var claims1 = jwtTokenService.extractClaims(token1);
        var claims2 = jwtTokenService.extractClaims(token2);

        String bodyHash1 = claims1.get("bodyHash", String.class);
        String bodyHash2 = claims2.get("bodyHash", String.class);

        // Les hash doivent être identiques pour le même body
        assertEquals(bodyHash1, bodyHash2);
    }

    @Test
    @DisplayName("Des bodies différents produisent des hash différents")
    void testBodyHash_DifferentBodies() {
        // Given
        String from = "+22891234567";
        String to = "ORABANK";

        // When - générer des tokens avec des bodies différents
        String token1 = jwtTokenService.generateWebhookToken(from, to, "SOLDE");
        String token2 = jwtTokenService.generateWebhookToken(from, to, "TRANSFERT");

        // Then - extraire les bodyHash
        var claims1 = jwtTokenService.extractClaims(token1);
        var claims2 = jwtTokenService.extractClaims(token2);

        String bodyHash1 = claims1.get("bodyHash", String.class);
        String bodyHash2 = claims2.get("bodyHash", String.class);

        // Les hash doivent être différents
        assertNotEquals(bodyHash1, bodyHash2);
    }
}
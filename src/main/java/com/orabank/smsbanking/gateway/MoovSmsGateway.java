package com.orabank.smsbanking.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orabank.smsbanking.util.LoggingUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Moov Africa Togo SMS gateway implementation.
 * Handles sending SMS messages through the Moov Africa Togo SMS API.
 * This is the primary gateway for Togo operations.
 * 
 * PRODUCTION CONFIGURATION:
 * - Connect timeout: 5000ms
 * - Read timeout: 10000ms
 * - Robust JSON response parsing
 * - Circuit breaker integration via Resilience4j
 * - Retry with exponential backoff
 * - Rate limiting protection
 * - Detailed error logging with masked phone numbers
 */
@Slf4j
@Service("moovSmsGateway")
public class MoovSmsGateway implements SmsGateway {
    
    private final String apiKey;
    private final String apiSecret;
    private final String apiUrl;
    private final String senderId;
    private final boolean moovEnabled;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    
    public MoovSmsGateway(
            @Value("${moov.sms.api.key:#{null}}") String apiKey,
            @Value("${moov.sms.api.secret:#{null}}") String apiSecret,
            @Value("${moov.sms.api.url:https://api.moov.tg/sms/v1/send}") String apiUrl,
            @Value("${moov.sms.sender.id:ORABANK}") String senderId,
            @Value("${moov.sms.enabled:true}") boolean moovEnabled,
            @Value("${moov.sms.connect.timeout.ms:5000}") int connectTimeoutMs,
            @Value("${moov.sms.read.timeout.ms:10000}") int readTimeoutMs) {
        
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiUrl = apiUrl;
        this.senderId = senderId;
        this.moovEnabled = moovEnabled;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = createRestTemplateWithTimeouts(connectTimeoutMs, readTimeoutMs);
        
        if (moovEnabled && apiKey != null && !apiKey.isEmpty()) {
            log.info("Moov Africa Togo SMS gateway configured with sender ID: {}, timeouts: connect={}ms, read={}ms", 
                    senderId, connectTimeoutMs, readTimeoutMs);
        } else {
            log.warn("Moov Africa Togo SMS gateway not configured - missing API key or disabled");
        }
    }
    
    /**
     * Creates a RestTemplate with custom connection and read timeouts.
     * Also configures DNS caching for better performance.
     */
    private RestTemplate createRestTemplateWithTimeouts(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        
        // Configure DNS caching to avoid repeated DNS lookups
        try {
            InetAddress.getByName("api.moov.tg");
            java.security.Security.setProperty("networkaddress.cache.ttl", "60");
            java.security.Security.setProperty("networkaddress.cache.negative.ttl", "10");
        } catch (UnknownHostException e) {
            log.debug("DNS cache configuration skipped - host not reachable during initialization");
        }
        
        return new RestTemplate(factory);
    }
    
    @Override
    @CircuitBreaker(name = "moovSmsGateway", fallbackMethod = "sendSmsFallback")
    @Retry(name = "moovSmsGateway", fallbackMethod = "sendSmsFallback")
    @RateLimiter(name = "moovSmsGateway")
    public boolean sendSms(String to, String message) {
        // Si Moov est désactivé, on retourne false pour permettre le fallback vers Togocel
        if (!moovEnabled) {
            log.warn("MOOV DISABLED - Gateway Moov désactivé. Aucun SMS envoyé à {}. Basculer vers Togocel si disponible.", 
                    LoggingUtil.maskPhoneNumber(to));
            return false;
        }
        
        // Vérifier que les identifiants sont présents
        if (!isAvailable()) {
            log.warn("MOOV NOT CONFIGURED - Identifiants API manquants pour Moov. Aucun SMS envoyé à {}. Configurez MOOV_SMS_API_KEY et MOOV_SMS_API_SECRET", 
                    LoggingUtil.maskPhoneNumber(to));
            return false;
        }
        
        try {
            log.debug("Attempting to send SMS via Moov Togo to: {}, message length: {}", 
                    LoggingUtil.maskPhoneNumber(to), message != null ? message.length() : 0);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-API-Secret", apiSecret);
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "OrabankSMSBanking/1.0");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("recipient", to);
            requestBody.put("message", message);
            requestBody.put("sender", senderId);
            
            // Paramètres optionnels selon l'API Moov
            requestBody.put("encoding", "UTF-8");
            requestBody.put("priority", "high");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("Sending SMS via Moov Togo to {} with sender {}", 
                    LoggingUtil.maskPhoneNumber(to), senderId);
            
            String response = restTemplate.postForObject(apiUrl, request, String.class);
            
            boolean success = parseMoovResponse(response, to);
            
            if (success) {
                log.info("SMS sent successfully via Moov Togo to {}", 
                        LoggingUtil.maskPhoneNumber(to));
            } else {
                log.warn("SMS failed via Moov Togo to {}, response: {}", 
                        LoggingUtil.maskPhoneNumber(to), response);
            }
            
            return success;
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("HTTP Server Error sending SMS via Moov Togo to {}: {} ({})", 
                    LoggingUtil.maskPhoneNumber(to), e.getStatusCode(), e.getMessage());
            return false;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Network error sending SMS via Moov Togo to {}: {}", 
                    LoggingUtil.maskPhoneNumber(to), e.getMessage());
            
            // Vérifier si c'est une erreur de connexion (Connection refused)
            if (e.getCause() != null && e.getCause().getMessage() != null && 
                e.getCause().getMessage().contains("Connection refused")) {
                log.error("MOOV CONNECTION REFUSED - Le serveur api.moov.tg est inaccessible.");
                log.error("CAUSES POSSIBLES:");
                log.error("  1. Firewall bloque les connexions sortantes vers api.moov.tg:443");
                log.error("  2. Problème DNS - le nom d'hôte api.moov.tg ne peut être résolu");
                log.error("  3. Le serveur Moov est hors ligne ou en maintenance");
                log.error("  4. VPN requis pour accéder à l'API Moov");
                log.error("ACTIONS CORRECTIVES:");
                log.error("  - Tester la connectivité: ping api.moov.tg && telnet api.moov.tg 443");
                log.error("  - Contacter Moov Africa Togo pour vérifier le statut de l'API");
                log.error("  - En développement: définir MOOV_SMS_ENABLED=false pour utiliser uniquement Togocel");
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error sending SMS via Moov Togo to {}: {}", 
                    LoggingUtil.maskPhoneNumber(to), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Fallback method pour CircuitBreaker et Retry.
     * Retourne false pour indiquer l'échec et permettre au router de basculer vers Togocel.
     */
    public boolean sendSmsFallback(String to, String message, Throwable t) {
        log.warn("Resilience4j fallback triggered for Moov gateway ({}): {}", 
                LoggingUtil.maskPhoneNumber(to), t.getClass().getSimpleName());
        return false;
    }
    
    /**
     * Parses the Moov API response to determine success or failure.
     * Handles various response formats robustly.
     * 
     * @param response the raw JSON response from Moov API
     * @param to the recipient phone number (for logging)
     * @return true if the SMS was sent successfully, false otherwise
     */
    private boolean parseMoovResponse(String response, String to) {
        if (response == null || response.trim().isEmpty()) {
            log.warn("Empty response from Moov API for {}", LoggingUtil.maskPhoneNumber(to));
            return false;
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            
            // Check for explicit success indicators
            if (rootNode.has("status")) {
                String status = rootNode.get("status").asText("");
                if ("success".equalsIgnoreCase(status) || "sent".equalsIgnoreCase(status) || 
                    "delivered".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status)) {
                    return true;
                }
                if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status) || 
                    "rejected".equalsIgnoreCase(status)) {
                    log.warn("Moov API returned status '{}' for {}", status, LoggingUtil.maskPhoneNumber(to));
                    return false;
                }
            }
            
            // Check for success field (boolean)
            if (rootNode.has("success") && rootNode.get("success").isBoolean()) {
                return rootNode.get("success").asBoolean(false);
            }
            
            // Check for error field
            if (rootNode.has("error")) {
                JsonNode errorNode = rootNode.get("error");
                if (errorNode.isTextual() && !errorNode.asText("").isEmpty()) {
                    log.warn("Moov API returned error: {} for {}", errorNode.asText(), LoggingUtil.maskPhoneNumber(to));
                    return false;
                }
                if (errorNode.isObject() && errorNode.has("message")) {
                    log.warn("Moov API returned error: {} for {}", 
                            errorNode.get("message").asText(), LoggingUtil.maskPhoneNumber(to));
                    return false;
                }
            }
            
            // Check for errorCode or code field indicating failure
            if (rootNode.has("errorCode") || rootNode.has("code")) {
                JsonNode codeNode = rootNode.has("errorCode") ? rootNode.get("errorCode") : rootNode.get("code");
                if (codeNode.isInt()) {
                    int code = codeNode.asInt(-1);
                    if (code != 0 && code != 200) {
                        log.warn("Moov API returned error code {} for {}", code, LoggingUtil.maskPhoneNumber(to));
                        return false;
                    }
                }
            }
            
            // Check for messageId or transactionId indicating success
            if (rootNode.has("messageId") || rootNode.has("transactionId") || rootNode.has("id")) {
                String messageId = rootNode.has("messageId") ? rootNode.get("messageId").asText("") :
                                   rootNode.has("transactionId") ? rootNode.get("transactionId").asText("") :
                                   rootNode.get("id").asText("");
                if (!messageId.isEmpty()) {
                    log.debug("Moov API returned message ID: {} for {}", messageId, LoggingUtil.maskPhoneNumber(to));
                    return true;
                }
            }
            
            // Default: assume success if no explicit error found
            log.debug("No explicit success/error found in Moov response, assuming success for {}", 
                    LoggingUtil.maskPhoneNumber(to));
            return true;
            
        } catch (IOException e) {
            log.error("Failed to parse Moov API response: {}", response, e);
            // If we can't parse but got a response, assume it might be OK
            // This handles cases where the API returns non-JSON success responses
            return !response.toLowerCase().contains("error") && 
                   !response.toLowerCase().contains("failed");
        }
    }
    
    @Override
    public String getProviderName() {
        return "Moov Africa Togo";
    }
    
    @Override
    public boolean isAvailable() {
        return moovEnabled &&
                apiKey != null && !apiKey.isEmpty() &&
                apiSecret != null && !apiSecret.isEmpty() &&
                apiUrl != null && !apiUrl.isEmpty();
    }
}

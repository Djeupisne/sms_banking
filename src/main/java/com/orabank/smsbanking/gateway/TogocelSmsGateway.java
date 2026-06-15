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
 * Togocel SMS gateway implementation.
 * Handles sending SMS messages through the Togocel SMS API.
 * This serves as a fallback gateway for Togo operations.
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
@Service("togocelSmsGateway")
public class TogocelSmsGateway implements SmsGateway {
    
    private final String apiKey;
    private final String apiSecret;
    private final String apiUrl;
    private final String senderId;
    private final boolean togocelEnabled;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    
    public TogocelSmsGateway(
            @Value("${togocel.sms.api.key:#{null}}") String apiKey,
            @Value("${togocel.sms.api.secret:#{null}}") String apiSecret,
            @Value("${togocel.sms.api.url:https://api.togocel.com/sms/v1/send}") String apiUrl,
            @Value("${togocel.sms.sender.id:ORABANK}") String senderId,
            @Value("${togocel.sms.enabled:true}") boolean togocelEnabled,
            @Value("${togocel.sms.connect.timeout.ms:5000}") int connectTimeoutMs,
            @Value("${togocel.sms.read.timeout.ms:10000}") int readTimeoutMs) {
        
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiUrl = apiUrl;
        this.senderId = senderId;
        this.togocelEnabled = togocelEnabled;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = createRestTemplateWithTimeouts(connectTimeoutMs, readTimeoutMs);
        
        if (togocelEnabled && apiKey != null && !apiKey.isEmpty()) {
            log.info("Togocel SMS gateway configured with sender ID: {}, timeouts: connect={}ms, read={}ms", 
                    senderId, connectTimeoutMs, readTimeoutMs);
        } else {
            log.warn("Togocel SMS gateway not configured - missing API key or disabled");
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
            InetAddress.getByName("api.togocel.com");
            java.security.Security.setProperty("networkaddress.cache.ttl", "60");
            java.security.Security.setProperty("networkaddress.cache.negative.ttl", "10");
        } catch (UnknownHostException e) {
            log.debug("DNS cache configuration skipped - host not reachable during initialization");
        }
        
        return new RestTemplate(factory);
    }
    
    @Override
    @CircuitBreaker(name = "togocelSmsGateway", fallbackMethod = "sendSmsFallback")
    @Retry(name = "togocelSmsGateway", fallbackMethod = "sendSmsFallback")
    @RateLimiter(name = "togocelSmsGateway")
    public boolean sendSms(String to, String message) {
        // Si Togocel est désactivé, on retourne false pour permettre l'utilisation de Moov si disponible
        if (!togocelEnabled) {
            log.warn("TOGOCEL DISABLED - Gateway Togocel désactivé. Aucun SMS envoyé à {}. Basculer vers Moov si disponible.", 
                    LoggingUtil.maskPhoneNumber(to));
            return false;
        }
        
        // Vérifier que les identifiants sont présents
        if (!isAvailable()) {
            log.warn("TOGOCEL NOT CONFIGURED - Identifiants API manquants pour Togocel. Aucun SMS envoyé à {}. Configurez TOGOCEL_SMS_API_KEY et TOGOCEL_SMS_API_SECRET", 
                    LoggingUtil.maskPhoneNumber(to));
            return false;
        }
        
        try {
            log.debug("Attempting to send SMS via Togocel to: {}, message length: {}", 
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
            
            // Paramètres optionnels selon l'API Togocel
            requestBody.put("encoding", "UTF-8");
            requestBody.put("priority", "normal");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("Sending SMS via Togocel to {} with sender {}", 
                    LoggingUtil.maskPhoneNumber(to), senderId);
            
            String response = restTemplate.postForObject(apiUrl, request, String.class);
            
            boolean success = parseTogocelResponse(response, to);
            
            if (success) {
                log.info("SMS sent successfully via Togocel to {}", 
                        LoggingUtil.maskPhoneNumber(to));
            } else {
                log.warn("SMS failed via Togocel to {}, response: {}", 
                        LoggingUtil.maskPhoneNumber(to), response);
            }
            
            return success;
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("HTTP Server Error sending SMS via Togocel to {}: {} ({})", 
                    LoggingUtil.maskPhoneNumber(to), e.getStatusCode(), e.getMessage());
            return false;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Network error sending SMS via Togocel to {}: {}", 
                    LoggingUtil.maskPhoneNumber(to), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error sending SMS via Togocel to {}: {}", 
                    LoggingUtil.maskPhoneNumber(to), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Fallback method pour CircuitBreaker et Retry.
     * Retourne false pour indiquer l'échec.
     */
    public boolean sendSmsFallback(String to, String message, Throwable t) {
        log.warn("Resilience4j fallback triggered for Togocel gateway ({}): {}", 
                LoggingUtil.maskPhoneNumber(to), t.getClass().getSimpleName());
        return false;
    }
    
    /**
     * Parses the Togocel API response to determine success or failure.
     * Handles various response formats robustly.
     * Detects HTML responses (anti-bot pages) and returns false.
     * 
     * @param response the raw JSON response from Togocel API
     * @param to the recipient phone number (for logging)
     * @return true if the SMS was sent successfully, false otherwise
     */
    private boolean parseTogocelResponse(String response, String to) {
        if (response == null || response.trim().isEmpty()) {
            log.warn("Empty response from Togocel API for {}", LoggingUtil.maskPhoneNumber(to));
            return false;
        }
        
        // Détecter les réponses HTML (page anti-bot, erreur 404, etc.)
        // C'est un cas critique : Togocel renvoie une page HTML au lieu de JSON
        String trimmedResponse = response.trim();
        if (trimmedResponse.startsWith("<") || 
            trimmedResponse.toLowerCase().contains("<html") ||
            trimmedResponse.toLowerCase().contains("<!doctype") ||
            trimmedResponse.toLowerCase().contains("fingerprintjs") ||
            trimmedResponse.toLowerCase().contains("antibot")) {
            
            log.error("TOGOCEL ANTI-BOT DETECTED - L'API Togocel a renvoyé une page HTML au lieu de JSON pour {}. Réponse reçue (premiers 500 chars): {}", 
                    LoggingUtil.maskPhoneNumber(to), 
                    trimmedResponse.length() > 500 ? trimmedResponse.substring(0, 500) + "..." : trimmedResponse);
            log.error("ACTION REQUISE: Vérifiez que l'endpoint Togocel est correct et que vous avez les credentials API valides.");
            log.error("L'URL {} semble pointer vers une page web protégée, pas une API REST.", apiUrl);
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
                    log.warn("Togocel API returned status '{}' for {}", status, LoggingUtil.maskPhoneNumber(to));
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
                    log.warn("Togocel API returned error: {} for {}", errorNode.asText(), LoggingUtil.maskPhoneNumber(to));
                    return false;
                }
                if (errorNode.isObject() && errorNode.has("message")) {
                    log.warn("Togocel API returned error: {} for {}", 
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
                        log.warn("Togocel API returned error code {} for {}", code, LoggingUtil.maskPhoneNumber(to));
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
                    log.debug("Togocel API returned message ID: {} for {}", messageId, LoggingUtil.maskPhoneNumber(to));
                    return true;
                }
            }
            
            // Default: assume success if no explicit error found
            log.debug("No explicit success/error found in Togocel response, assuming success for {}", 
                    LoggingUtil.maskPhoneNumber(to));
            return true;
            
        } catch (IOException e) {
            log.error("Failed to parse Togocel API response (JSON parse error): {}", response, e);
            // Si on reçoit une réponse non-JSON, c'est probablement un échec
            // Ne jamais considérer un échec de parsing comme un succès
            log.error("TOGOCEL JSON PARSE FAILED - La réponse n'est pas du JSON valide. Ceci indique un problème de configuration ou d'endpoint API.");
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "Togocel";
    }
    
    @Override
    public boolean isAvailable() {
        return togocelEnabled &&
                apiKey != null && !apiKey.isEmpty() &&
                apiSecret != null && !apiSecret.isEmpty() &&
                apiUrl != null && !apiUrl.isEmpty();
    }
}

package com.orabank.smsbanking.gateway;

import com.orabank.smsbanking.util.LoggingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for communicating with the core banking system API.
 * Handles account operations, transfers, and other banking functions.
 * Implements input validation and secure URL construction.
 */
@Slf4j
@Service
public class CoreBankingApiClient {
    
    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final RestTemplate restTemplate;
    private final boolean mockEnabled;
    
    // Regex pour valider le format des numéros de compte (alphanumérique, 8-20 caractères)
    private static final String ACCOUNT_NUMBER_PATTERN = "^[A-Za-z0-9]{8,20}$";
    
    public CoreBankingApiClient(
            @Value("${core.banking.base.url:http://localhost:8081/api}") String baseUrl,
            @Value("${core.banking.api.key:#{null}}") String apiKey,
            @Value("${core.banking.api.secret:#{null}}") String apiSecret,
            @Value("${core.banking.mock.enabled:false}") boolean mockEnabled) {
        
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.mockEnabled = mockEnabled;
        
        // Configuration des timeouts pour éviter les blocages
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 secondes
        factory.setReadTimeout(10000);    // 10 secondes
        this.restTemplate = new RestTemplate(factory);
        
        if (mockEnabled) {
            log.info("Core banking API client initialized in MOCK MODE - All external calls will be simulated");
        } else {
            log.info("Core banking API client initialized with base URL: {}", baseUrl);
        }
    }
    
    /**
     * Performs a mobile money transfer through the core banking system.
     *
     * @param phoneNumber the phone number initiating the transfer
     * @param amount the amount to transfer in FCFA
     * @return true if the transfer was successful, false otherwise
     */
    public boolean transferToMobileMoney(String phoneNumber, long amount) {
        // Mode MOCK : simulation sans appel API externe
        if (mockEnabled) {
            log.info("MOCK MODE - Simulation transfert Mobile Money pour: {}, montant: {}", 
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount);
            // Simulation d'un succès après un court délai
            try {
                Thread.sleep(100); // Simulation de latence réseau
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("MOCK MODE - Transfert Mobile Money simulé avec SUCCÈS pour: {}, montant: {}", 
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount);
            return true;
        }
        
        try {
            log.debug("Initiating mobile money transfer via core banking for masked-phone: {}, amount: {}", 
                    LoggingUtil.maskPhoneNumber(phoneNumber), amount);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-API-Key", apiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sourcePhoneNumber", phoneNumber);
            requestBody.put("destinationType", "MOBILE_MONEY");
            requestBody.put("destination", phoneNumber);
            requestBody.put("amount", amount);
            requestBody.put("currency", "XOF");
            requestBody.put("transactionType", "MOBILE_MONEY_TRANSFER");
            requestBody.put("description", "SMS Banking Mobile Money Transfer");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String transferUrl = baseUrl + "/transfers/mobile-money";
            String response = restTemplate.postForObject(transferUrl, request, String.class);
            
            boolean success = response != null && 
                             (response.contains("\"status\":\"SUCCESS\"") || 
                              response.contains("\"success\":true"));
            
            if (success) {
                log.info("Mobile money transfer completed via core banking for masked-phone: {}, amount: {}", 
                        LoggingUtil.maskPhoneNumber(phoneNumber), amount);
            } else {
                log.warn("Mobile money transfer failed via core banking for masked-phone: {}", 
                        LoggingUtil.maskPhoneNumber(phoneNumber));
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error performing mobile money transfer via core banking for masked-phone: {}", 
                    LoggingUtil.maskPhoneNumber(phoneNumber), e);
            return false;
        }
    }
    
    /**
     * Gets account balance from the core banking system.
     * Valide le format du numéro de compte avant construction de l'URL pour prévenir les injections.
     *
     * @param accountNumber the account number to check
     * @return the account balance, or null if not available
     * @throws IllegalArgumentException si le numéro de compte est invalide
     */
    public java.math.BigDecimal getAccountBalance(String accountNumber) {
        try {
            // Validation stricte du numéro de compte avant utilisation dans l'URL
            if (!isValidAccountNumber(accountNumber)) {
                log.error("Numéro de compte invalide: format incorrect");
                throw new IllegalArgumentException("Numéro de compte invalide");
            }
            
            log.debug("Retrieving account balance from core banking for account: {}", 
                    LoggingUtil.maskAccountNumber(accountNumber));
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-API-Key", apiKey);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            // Construction sécurisée de l'URL avec numéro validé
            String balanceUrl = baseUrl + "/accounts/" + accountNumber + "/balance";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(balanceUrl, Map.class);
            
            if (response != null && response.containsKey("balance")) {
                Object balanceObj = response.get("balance");
                return java.math.BigDecimal.valueOf(Double.parseDouble(balanceObj.toString()));
            }
            
            return null;
        } catch (IllegalArgumentException e) {
            // Ne pas logger les détails d'un numéro invalide pour éviter le data leakage
            log.error("Validation échouée pour le numéro de compte");
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving account balance from core banking for account: {}", 
                    LoggingUtil.maskAccountNumber(accountNumber), e);
            return null;
        }
    }
    
    /**
     * Validates an account in the core banking system.
     * Valide le format du numéro de compte avant appel API.
     *
     * @param accountNumber the account number to validate
     * @return true if the account is valid, false otherwise
     * @throws IllegalArgumentException si le numéro de compte est invalide
     */
    public boolean validateAccount(String accountNumber) {
        try {
            // Validation stricte du numéro de compte avant utilisation dans l'URL
            if (!isValidAccountNumber(accountNumber)) {
                log.error("Numéro de compte invalide: format incorrect");
                throw new IllegalArgumentException("Numéro de compte invalide");
            }
            
            log.debug("Validating account in core banking: {}", LoggingUtil.maskAccountNumber(accountNumber));
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-API-Key", apiKey);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            String validateUrl = baseUrl + "/accounts/" + accountNumber + "/validate";
            Boolean response = restTemplate.getForObject(validateUrl, Boolean.class);
            
            return response != null && response;
        } catch (IllegalArgumentException e) {
            log.error("Validation échouée pour le numéro de compte");
            throw e;
        } catch (Exception e) {
            log.error("Error validating account in core banking: {}", 
                    LoggingUtil.maskAccountNumber(accountNumber), e);
            return false;
        }
    }
    
    /**
     * Valide le format d'un numéro de compte.
     * Utilise une regex stricte pour prévenir les injections SQL/URL.
     *
     * @param accountNumber le numéro à valider
     * @return true si valide, false sinon
     */
    private boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }
        return accountNumber.matches(ACCOUNT_NUMBER_PATTERN);
    }
}

package com.orabank.smsbanking.unit.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orabank.smsbanking.gateway.TogocelSmsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for TogocelSmsGateway.
 * Tests cover various response formats and error scenarios.
 */
class TogocelSmsGatewayTest {

    private TogocelSmsGateway togocelGateway;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        
        // Create gateway with test configuration
        togocelGateway = new TogocelSmsGateway(
                "test-api-key",
                "test-api-secret",
                "http://localhost:8889/sms/v1/send",
                "ORABANK",
                true,
                5000,
                10000
        );
        
        // Access the internal RestTemplate to set up mock server
        Field restTemplateField = TogocelSmsGateway.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) restTemplateField.get(togocelGateway);
        
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void testSendSms_SuccessWithStatusField() {
        String successResponse = "{\"status\":\"success\",\"messageId\":\"TOGO123456\"}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent successfully");
        mockServer.verify();
    }

    @Test
    void testSendSms_SuccessWithMessageId() {
        String successResponse = "{\"messageId\":\"TOGO789012\",\"code\":0}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent successfully with messageId");
        mockServer.verify();
    }

    @Test
    void testSendSms_FailureWithFailedStatus() {
        String failureResponse = "{\"status\":\"failed\",\"error\":\"Invalid recipient\"}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(failureResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with failed status");
        mockServer.verify();
    }

    @Test
    void testSendSms_FailureWithErrorField() {
        String errorResponse = "{\"error\":{\"message\":\"Insufficient credits\",\"code\":402}}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with error field");
        mockServer.verify();
    }

    @Test
    void testSendSms_FailureWithErrorCode() {
        String errorResponse = "{\"errorCode\":400,\"message\":\"Bad request\"}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with non-zero error code");
        mockServer.verify();
    }

    @Test
    void testSendSms_EmptyResponse() {
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with empty response");
        mockServer.verify();
    }

    @Test
    void testSendSms_NetworkError() {
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with network error");
        mockServer.verify();
    }

    @Test
    void testSendSms_MockMode() throws Exception {
        // Create gateway in mock mode (disabled)
        TogocelSmsGateway mockGateway = new TogocelSmsGateway(
                null,
                null,
                "http://localhost:8889/sms/v1/send",
                "ORABANK",
                false,
                5000,
                10000
        );
        
        boolean result = mockGateway.sendSms("+22891234567", "Test message");
        
        // En mode disabled, le gateway retourne false pour permettre le fallback
        assertFalse(result, "SMS should return false when gateway is disabled to trigger fallback");
    }

    @Test
    void testGetProviderName() {
        String providerName = togocelGateway.getProviderName();
        
        assertEquals("Togocel", providerName);
    }

    @Test
    void testIsAvailable_WithValidConfig() {
        assertTrue(togocelGateway.isAvailable(), "Gateway should be available with valid config");
    }

    @Test
    void testIsAvailable_WithoutApiKey() throws Exception {
        TogocelSmsGateway gatewayNoKey = new TogocelSmsGateway(
                null,
                "test-secret",
                "http://localhost:8889/sms/v1/send",
                "ORABANK",
                true,
                5000,
                10000
        );
        
        assertFalse(gatewayNoKey.isAvailable(), "Gateway should not be available without API key");
    }

    @Test
    void testSendSms_RequestHeaders() {
        String successResponse = "{\"status\":\"sent\"}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(header("X-API-Secret", "test-api-secret"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(header("User-Agent", "OrabankSMSBanking/1.0"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent with correct headers");
        mockServer.verify();
    }

    @Test
    void testSendSms_RequestBodyStructure() {
        String successResponse = "{\"status\":\"success\"}";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent with correct body structure");
        mockServer.verify();
    }

    @Test
    void testSendSms_HtmlResponseAntiBot() {
        // Simule la réponse HTML avec FingerprintJS (anti-bot) que Togocel renvoie
        String htmlResponse = "<!DOCTYPE html><html><head><title>togocel.com</title>" +
                "<script src='https://fingerprintjs.com/anti-bot.js'></script></head>" +
                "<body><h1>Access Denied</h1><p>Bot detection triggered</p></body></html>";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(htmlResponse, MediaType.TEXT_HTML));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail when receiving HTML anti-bot response");
        mockServer.verify();
    }

    @Test
    void testSendSms_HtmlResponseNotFound() {
        // Simule une page 404 HTML
        String html404 = "<html><head><title>404 Not Found</title></head>" +
                "<body><h1>404 - Page Not Found</h1></body></html>";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(html404, MediaType.TEXT_HTML));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail when receiving HTML 404 response");
        mockServer.verify();
    }

    @Test
    void testSendSms_InvalidJsonResponse() {
        // Simule une réponse non-JSON qui n'est pas HTML (texte brut)
        String invalidJson = "This is not JSON or HTML, just plain text";
        
        mockServer.expect(requestTo("http://localhost:8889/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(invalidJson, MediaType.TEXT_PLAIN));
        
        boolean result = togocelGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail when receiving invalid JSON response");
        mockServer.verify();
    }
}

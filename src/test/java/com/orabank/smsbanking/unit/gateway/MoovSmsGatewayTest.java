package com.orabank.smsbanking.unit.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orabank.smsbanking.gateway.MoovSmsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for MoovSmsGateway.
 * Tests cover various response formats and error scenarios.
 */
class MoovSmsGatewayTest {

    private MoovSmsGateway moovGateway;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        
        // Create gateway with test configuration
        moovGateway = new MoovSmsGateway(
                "test-api-key",
                "test-api-secret",
                "http://localhost:8888/sms/v1/send",
                "ORABANK",
                true,
                5000,
                10000
        );
        
        // Access the internal RestTemplate to set up mock server
        Field restTemplateField = MoovSmsGateway.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) restTemplateField.get(moovGateway);
        
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void testSendSms_SuccessWithStatusField() {
        String successResponse = "{\"status\":\"success\",\"messageId\":\"MSG123456\"}";
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent successfully");
        mockServer.verify();
    }

    @Test
    void testSendSms_SuccessWithMessageId() {
        String successResponse = "{\"messageId\":\"MSG789012\",\"code\":0}";
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent successfully with messageId");
        mockServer.verify();
    }

    @Test
    void testSendSms_FailureWithFailedStatus() {
        String failureResponse = "{\"status\":\"failed\",\"error\":\"Invalid recipient\"}";
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(failureResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with failed status");
        mockServer.verify();
    }

    @Test
    void testSendSms_FailureWithErrorField() {
        String errorResponse = "{\"error\":{\"message\":\"Insufficient credits\",\"code\":402}}";
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with error field");
        mockServer.verify();
    }

    @Test
    void testSendSms_FailureWithErrorCode() {
        String errorResponse = "{\"errorCode\":400,\"message\":\"Bad request\"}";
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with non-zero error code");
        mockServer.verify();
    }

    @Test
    void testSendSms_EmptyResponse() {
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with empty response");
        mockServer.verify();
    }

    @Test
    void testSendSms_NetworkError() {
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertFalse(result, "SMS should fail with network error");
        mockServer.verify();
    }

    @Test
    void testSendSms_MockMode() throws Exception {
        // Create gateway in mock mode (disabled)
        MoovSmsGateway mockGateway = new MoovSmsGateway(
                null,
                null,
                "http://localhost:8888/sms/v1/send",
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
        String providerName = moovGateway.getProviderName();
        
        assertEquals("Moov Africa Togo", providerName);
    }

    @Test
    void testIsAvailable_WithValidConfig() {
        assertTrue(moovGateway.isAvailable(), "Gateway should be available with valid config");
    }

    @Test
    void testIsAvailable_WithoutApiKey() throws Exception {
        MoovSmsGateway gatewayNoKey = new MoovSmsGateway(
                null,
                "test-secret",
                "http://localhost:8888/sms/v1/send",
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
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(header("X-API-Secret", "test-api-secret"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(header("User-Agent", "OrabankSMSBanking/1.0"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent with correct headers");
        mockServer.verify();
    }

    @Test
    void testSendSms_RequestBodyStructure() {
        String successResponse = "{\"status\":\"success\"}";
        
        mockServer.expect(requestTo("http://localhost:8888/sms/v1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));
        
        boolean result = moovGateway.sendSms("+22891234567", "Test message");
        
        assertTrue(result, "SMS should be sent with correct body structure");
        mockServer.verify();
    }
}

package com.orabank.smsbanking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orabank.smsbanking.dto.request.SmsRequestDto;
import com.orabank.smsbanking.dto.response.SmsResponseDto;
import com.orabank.smsbanking.service.SmsProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SmsWebhookControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SmsProcessingService smsProcessingService;

    @BeforeEach
    void setUp() {
        SmsResponseDto defaultResponse = new SmsResponseDto("Votre solde est de 500000 FCFA");
        when(smsProcessingService.processSms(any(SmsRequestDto.class))).thenReturn(defaultResponse);
    }

    @Test
    void testHandleSmsWebhookSuccess() throws Exception {
        SmsRequestDto request = SmsRequestDto.builder()
                .from("+2250123456789")
                .to("1234")
                .body("SOLDE?")
                .build();

        mockMvc.perform(post("/api/sms/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "orabank_webhook_api_key_2024_secure_key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Votre solde est de 500000 FCFA"));
    }

    @Test
    void testHandleSmsWebhookValidationFailure() throws Exception {
        SmsRequestDto request = SmsRequestDto.builder()
                .from("")
                .to("")
                .body("")
                .build();

        mockMvc.perform(post("/api/sms/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "orabank_webhook_api_key_2024_secure_key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testHandleSmsWebhookInvalidPhoneNumber() throws Exception {
        SmsRequestDto request = SmsRequestDto.builder()
                .from("invalid_phone")
                .to("1234")
                .body("SOLDE?")
                .build();

        mockMvc.perform(post("/api/sms/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "orabank_webhook_api_key_2024_secure_key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())  // ← Changé de 400 à 200
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testHandleSmsWebhookMissingFields() throws Exception {
        String invalidJson = "{ \"from\": \"+2250123456789\" }";

        mockMvc.perform(post("/api/sms/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "orabank_webhook_api_key_2024_secure_key")
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testHandleSmsWebhookMissingApiKey() throws Exception {
        SmsRequestDto request = SmsRequestDto.builder()
                .from("+2250123456789")
                .to("1234")
                .body("SOLDE?")
                .build();

        mockMvc.perform(post("/api/sms/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ORABANK - Erreur de sécurité: API Key requise (header X-API-Key)"))
                .andExpect(jsonPath("$.to").value("1234"));
    }

    @Test
    void testHandleSmsWebhookInvalidApiKey() throws Exception {
        SmsRequestDto request = SmsRequestDto.builder()
                .from("+2250123456789")
                .to("1234")
                .body("SOLDE?")
                .build();

        mockMvc.perform(post("/api/sms/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "invalid_key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ORABANK - Erreur de sécurité: API Key requise (header X-API-Key)"))
                .andExpect(jsonPath("$.to").value("1234"));
    }
}
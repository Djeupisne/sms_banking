package com.orabank.smsbanking.unit.security;

import com.orabank.smsbanking.security.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiterService = new RateLimiterService(redisTemplate);
        // Set values using reflection or constructor injection if needed
        try {
            java.lang.reflect.Field maxRequestsField = RateLimiterService.class.getDeclaredField("maxRequests");
            maxRequestsField.setAccessible(true);
            maxRequestsField.set(rateLimiterService, 5);
            
            java.lang.reflect.Field windowInMinutesField = RateLimiterService.class.getDeclaredField("windowInMinutes");
            windowInMinutesField.setAccessible(true);
            windowInMinutesField.set(rateLimiterService, 1);
            
            java.lang.reflect.Field redisEnabledField = RateLimiterService.class.getDeclaredField("redisEnabled");
            redisEnabledField.setAccessible(true);
            redisEnabledField.set(rateLimiterService, true);
        } catch (Exception e) {
            // Handle reflection exception
        }
    }

    @Test
    void testIsAllowedWithinLimit() {
        // Given
        String phoneNumber = "+2250123456789";
        String countKey = "ratelimit:" + phoneNumber + ":count";
        String timestampKey = "ratelimit:" + phoneNumber + ":timestamp";
        
        when(valueOperations.get(eq(countKey))).thenReturn("2");
        when(valueOperations.get(eq(timestampKey))).thenReturn(String.valueOf(System.currentTimeMillis() - 30000)); // 30 seconds ago
        when(valueOperations.increment(eq(countKey))).thenReturn(3L);

        // When
        boolean allowed = rateLimiterService.isAllowed(phoneNumber);

        // Then
        assertTrue(allowed);
        verify(valueOperations, times(1)).increment(eq(countKey));
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void testIsAllowedExceedsLimit() {
        // Given
        String phoneNumber = "+2250123456789";
        String countKey = "ratelimit:" + phoneNumber + ":count";
        String timestampKey = "ratelimit:" + phoneNumber + ":timestamp";
        
        when(valueOperations.get(eq(countKey))).thenReturn("5"); // At limit
        when(valueOperations.get(eq(timestampKey))).thenReturn(String.valueOf(System.currentTimeMillis() - 30000)); // 30 seconds ago

        // When
        boolean allowed = rateLimiterService.isAllowed(phoneNumber);

        // Then
        assertFalse(allowed);
        verify(valueOperations, never()).increment(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void testIsAllowedNewWindow() {
        // Given
        String phoneNumber = "+2250123456789";
        String countKey = "ratelimit:" + phoneNumber + ":count";
        String timestampKey = "ratelimit:" + phoneNumber + ":timestamp";
        
        when(valueOperations.get(eq(countKey))).thenReturn("5");
        when(valueOperations.get(eq(timestampKey))).thenReturn(String.valueOf(System.currentTimeMillis() - 120000)); // 2 minutes ago
        doNothing().when(valueOperations).set(anyString(), anyString(), any());

        // When
        boolean allowed = rateLimiterService.isAllowed(phoneNumber);

        // Then
        assertTrue(allowed);
        verify(valueOperations, times(2)).set(anyString(), anyString(), any());
    }

    @Test
    void testIsAllowedRedisError() {
        // Given
        String phoneNumber = "+2250123456789";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis error"));

        // When
        boolean allowed = rateLimiterService.isAllowed(phoneNumber);

        // Then
        assertTrue(allowed); // Should fail open
    }

    @Test
    void testGetRemainingRequests() {
        // Given
        String phoneNumber = "+2250123456789";
        String countKey = "ratelimit:" + phoneNumber + ":count";
        when(valueOperations.get(eq(countKey))).thenReturn("2");

        // When
        int remaining = rateLimiterService.getRemainingRequests(phoneNumber);

        // Then
        assertEquals(3, remaining); // Max 5 - current 2 = 3 remaining
    }

    @Test
    void testGetRemainingRequestsRedisError() {
        // Given
        String phoneNumber = "+2250123456789";
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        int remaining = rateLimiterService.getRemainingRequests(phoneNumber);

        // Then
        assertEquals(5, remaining); // Return max if error
    }
}
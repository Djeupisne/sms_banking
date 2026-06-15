package com.orabank.smsbanking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Resilience4j pour les gateways SMS Moov et Togocel.
 * Fournit des instances dédiées de CircuitBreaker, Retry et RateLimiter
 * pour chaque provider SMS avec monitoring via Actuator.
 */
@Configuration
public class Resilience4jConfig {

    /**
     * CircuitBreaker configuration pour Moov Africa Togo.
     * - Seuil d'échec : 50%
     * - Fenêtre glissante : 10 appels
     * - Minimum d'appels avant activation : 5
     * - Durée en état OPEN : 30 secondes
     * - État HALF-OPEN : 3 appels test
     */
    @Bean
    public CircuitBreaker moovCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(
                        java.io.IOException.class,
                        java.net.ConnectException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
                .build();

        return CircuitBreakerRegistry.of(config).circuitBreaker("moovSmsGateway");
    }

    /**
     * CircuitBreaker configuration pour Togocel.
     * Configuration identique à Moov pour cohérence.
     */
    @Bean
    public CircuitBreaker togocelCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(
                        java.io.IOException.class,
                        java.net.ConnectException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
                .build();

        return CircuitBreakerRegistry.of(config).circuitBreaker("togocelSmsGateway");
    }

    /**
     * Retry configuration pour Moov Africa Togo.
     * - 3 tentatives maximum
     * - Backoff exponentiel : 1s, 2s, 4s
     * - Retry sur exceptions réseau et timeout
     */
    @Bean
    public Retry moovRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .retryExceptions(
                        java.io.IOException.class,
                        java.net.ConnectException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
                .build();

        return RetryRegistry.of(config).retry("moovSmsGateway");
    }

    /**
     * Retry configuration pour Togocel.
     * Configuration identique à Moov pour cohérence.
     */
    @Bean
    public Retry togocelRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .retryExceptions(
                        java.io.IOException.class,
                        java.net.ConnectException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
                .build();

        return RetryRegistry.of(config).retry("togocelSmsGateway");
    }

    /**
     * RateLimiter configuration pour Moov Africa Togo.
     * - 10 appels par seconde
     * - Timeout d'attente : 500ms
     */
    @Bean
    public RateLimiter moovRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter("moovSmsGateway");
    }

    /**
     * RateLimiter configuration pour Togocel.
     * - 10 appels par seconde (peut être ajusté selon le contrat Togocel)
     * - Timeout d'attente : 500ms
     */
    @Bean
    public RateLimiter togocelRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter("togocelSmsGateway");
    }

    /**
     * Registry global pour monitoring Actuator.
     * Permet d'exposer les métriques via /actuator/circuitbreakers
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        Map<String, CircuitBreakerConfig> configs = new HashMap<>();
        
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        
        configs.put("moovSmsGateway", defaultConfig);
        configs.put("togocelSmsGateway", defaultConfig);
        
        return CircuitBreakerRegistry.of(configs);
    }

    /**
     * Registry global Retry pour monitoring Actuator.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        Map<String, RetryConfig> configs = new HashMap<>();
        
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .build();
        
        configs.put("moovSmsGateway", defaultConfig);
        configs.put("togocelSmsGateway", defaultConfig);
        
        return RetryRegistry.of(configs);
    }

    /**
     * Registry global RateLimiter pour monitoring Actuator.
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        Map<String, RateLimiterConfig> configs = new HashMap<>();
        
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build();
        
        configs.put("moovSmsGateway", defaultConfig);
        configs.put("togocelSmsGateway", defaultConfig);
        
        return RateLimiterRegistry.of(configs);
    }
}

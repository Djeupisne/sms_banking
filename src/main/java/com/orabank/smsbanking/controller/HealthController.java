package com.orabank.smsbanking.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final RedisTemplate<String, String> redisTemplate;
    private final DataSource dataSource;  // ← AJOUTER CETTE INJECTION

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.debug("Health check demandé");
        
        Map<String, Object> health = new HashMap<>();
        Map<String, String> components = new HashMap<>();
        
        // Vérification PostgreSQL (CORRIGÉE)
        boolean postgresUp = checkPostgresHealth();
        components.put("database", postgresUp ? "UP" : "DOWN");
        
        // Vérification Redis
        boolean redisUp = checkRedisHealth();
        components.put("redis", redisUp ? "UP" : "DOWN");
        
        // Vérification SMS Gateway
        boolean smsGatewayUp = checkSmsGatewayHealth();
        components.put("smsGateway", smsGatewayUp ? "UP" : "DOWN");
        
        boolean allHealthy = postgresUp && redisUp && smsGatewayUp;
        
        health.put("status", allHealthy ? "UP" : "DOWN");
        health.put("timestamp", System.currentTimeMillis());
        health.put("components", components);
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        return ResponseEntity.ok(status);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        log.debug("Readiness check demandé");
        
        Map<String, Object> readiness = new HashMap<>();
        
        boolean postgresUp = checkPostgresHealth();
        boolean redisUp = checkRedisHealth();
        boolean allHealthy = postgresUp && redisUp;
        
        readiness.put("status", allHealthy ? "UP" : "DOWN");
        readiness.put("ready", allHealthy);
        
        if (!allHealthy) {
            Map<String, Boolean> checks = new HashMap<>();
            checks.put("postgres", postgresUp);
            checks.put("redis", redisUp);
            readiness.put("checks", checks);
        }
        
        return ResponseEntity.ok(readiness);
    }
    
    /**
     * Vérifie la connectivité à PostgreSQL.
     * Exécute une requête simple "SELECT 1".
     */
    private boolean checkPostgresHealth() {
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(5); // Timeout 5 secondes
            log.debug("PostgreSQL health check: {}", isValid ? "UP" : "DOWN");
            return isValid;
        } catch (SQLException e) {
            log.error("PostgreSQL health check failed", e);
            return false;
        }
    }
    
    /**
     * Vérifie la connectivité à Redis.
     */
    private boolean checkRedisHealth() {
        try {
            redisTemplate.opsForValue().set("health:ping", "pong", 1, TimeUnit.SECONDS);
            String result = redisTemplate.opsForValue().get("health:ping");
            boolean isUp = "pong".equals(result);
            log.debug("Redis health check: {}", isUp ? "UP" : "DOWN");
            return isUp;
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }
    
    /**
     * Vérifie la disponibilité du SMS Gateway.
     */
    private boolean checkSmsGatewayHealth() {
        // Simulation - à adapter selon le provider
        log.debug("SMS Gateway health check passed (simulated)");
        return true;
    }
}

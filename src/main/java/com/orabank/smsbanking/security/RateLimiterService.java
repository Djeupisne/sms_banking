package com.orabank.smsbanking.security;

import com.orabank.smsbanking.exception.RateLimitExceededException;
import com.orabank.smsbanking.util.LoggingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for implementing rate limiting functionality.
 * Uses Redis as primary storage with in-memory fallback when Redis is unavailable.
 * Implements circuit breaker pattern for Redis failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${rate.limit.requests:5}")
    private int maxRequests;
    
    @Value("${rate.limit.window.minutes:1}")
    private int windowInMinutes;
    
    @Value("${rate.limit.redis.enabled:true}")
    private boolean redisEnabled;
    
    // In-memory fallback storage: phoneNumber -> (count, windowStartTime)
    private final Map<String, RateLimitEntry> inMemoryStore = new ConcurrentHashMap<>();
    
    // Circuit breaker state
    private volatile boolean redisAvailable = true;
    private volatile long lastRedisFailureTime = 0;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30000; // 30 seconds
    
    /**
     * Checks if a request from the given phone number is allowed based on rate limits.
     * Uses Redis as primary storage with in-memory fallback.
     * Implements fail-open strategy with circuit breaker.
     *
     * @param phoneNumber the phone number making the request
     * @return true if the request is allowed, false if rate limit is exceeded
     */
    public boolean isAllowed(String phoneNumber) {
        // Try Redis first if enabled and available
        if (redisEnabled && redisAvailable) {
            try {
                boolean result = checkRateLimitWithRedis(phoneNumber);
                // Success - mark Redis as available
                redisAvailable = true;
                return result;
            } catch (Exception e) {
                log.warn("Redis rate limit check failed, switching to in-memory fallback", e);
                redisAvailable = false;
                lastRedisFailureTime = System.currentTimeMillis();
            }
        }
        
        // Check if we should try Redis again after cooldown
        if (redisEnabled && !redisAvailable && 
            System.currentTimeMillis() - lastRedisFailureTime > CIRCUIT_BREAKER_COOLDOWN_MS) {
            log.info("Circuit breaker cooldown elapsed, attempting to reconnect to Redis");
            redisAvailable = true;
            return isAllowed(phoneNumber); // Recursive call to try Redis again
        }
        
        // Fallback to in-memory rate limiting
        log.debug("Using in-memory rate limiting for phone: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
        return checkRateLimitInMemory(phoneNumber);
    }
    
    /**
     * Rate limit check using Redis.
     */
    private boolean checkRateLimitWithRedis(String phoneNumber) {
        String key = "ratelimit:" + phoneNumber;
        String countKey = key + ":count";
        String timestampKey = key + ":timestamp";
        
        String currentCountStr = redisTemplate.opsForValue().get(countKey);
        String lastAccessStr = redisTemplate.opsForValue().get(timestampKey);
        
        long currentCount = currentCountStr != null ? Long.parseLong(currentCountStr) : 0;
        long lastAccess = lastAccessStr != null ? Long.parseLong(lastAccessStr) : 0;
        long now = System.currentTimeMillis();
        
        if (now - lastAccess > TimeUnit.MINUTES.toMillis(windowInMinutes)) {
            redisTemplate.opsForValue().set(countKey, "1", Duration.ofMinutes(windowInMinutes));
            redisTemplate.opsForValue().set(timestampKey, String.valueOf(now), Duration.ofMinutes(windowInMinutes));
            log.debug("Reset rate limit counter for masked-phone: {}", LoggingUtil.maskPhoneNumber(phoneNumber));
            return true;
        }
        
        if (currentCount >= maxRequests) {
            log.warn("Rate limit exceeded for masked-phone: {} ({} requests in {} min)", 
                    LoggingUtil.maskPhoneNumber(phoneNumber), currentCount, windowInMinutes);
            return false;
        }
        
        redisTemplate.opsForValue().increment(countKey);
        log.debug("Incremented rate limit counter for masked-phone: {} (count: {})", 
                LoggingUtil.maskPhoneNumber(phoneNumber), currentCount + 1);
        
        return true;
    }
    
    /**
     * Rate limit check using in-memory storage (fallback).
     */
    private boolean checkRateLimitInMemory(String phoneNumber) {
        long now = System.currentTimeMillis();
        long windowMs = TimeUnit.MINUTES.toMillis(windowInMinutes);
        
        RateLimitEntry entry = inMemoryStore.computeIfAbsent(phoneNumber, 
            k -> new RateLimitEntry(0, now));
        
        // Check if window has expired
        if (now - entry.windowStartTime > windowMs) {
            entry.count.set(0);
            entry.windowStartTime = now;
            log.debug("Reset in-memory rate limit counter for masked-phone: {}", 
                     LoggingUtil.maskPhoneNumber(phoneNumber));
        }
        
        // Check if limit exceeded
        if (entry.count.get() >= maxRequests) {
            log.warn("In-memory rate limit exceeded for masked-phone: {} ({} requests in {} min)", 
                    LoggingUtil.maskPhoneNumber(phoneNumber), entry.count.get(), windowInMinutes);
            return false;
        }
        
        // Increment counter
        entry.count.incrementAndGet();
        log.debug("Incremented in-memory rate limit counter for masked-phone: {} (count: {})", 
                LoggingUtil.maskPhoneNumber(phoneNumber), entry.count.get());
        
        return true;
    }
    
    /**
     * Gets the remaining number of requests allowed for a phone number.
     *
     * @param phoneNumber the phone number to check
     * @return the number of remaining requests
     */
    public int getRemainingRequests(String phoneNumber) {
        if (redisEnabled && redisAvailable) {
            try {
                String key = "ratelimit:" + phoneNumber + ":count";
                String countStr = redisTemplate.opsForValue().get(key);
                long currentCount = countStr != null ? Long.parseLong(countStr) : 0;
                return Math.max(0, maxRequests - (int) currentCount);
            } catch (Exception e) {
                log.debug("Redis unavailable, using in-memory for remaining requests");
            }
        }
        
        // Fallback to in-memory
        RateLimitEntry entry = inMemoryStore.get(phoneNumber);
        if (entry == null) {
            return maxRequests;
        }
        
        // Check if window has expired
        long now = System.currentTimeMillis();
        long windowMs = TimeUnit.MINUTES.toMillis(windowInMinutes);
        if (now - entry.windowStartTime > windowMs) {
            return maxRequests;
        }
        
        return Math.max(0, maxRequests - entry.count.get());
    }
    
    /**
     * Resets the rate limit counter for a phone number.
     *
     * @param phoneNumber the phone number to reset
     */
    public void resetRateLimit(String phoneNumber) {
        // Reset in Redis
        if (redisEnabled && redisAvailable) {
            try {
                String key = "ratelimit:" + phoneNumber;
                redisTemplate.delete(key + ":count");
                redisTemplate.delete(key + ":timestamp");
                log.info("Redis rate limit counter reset for masked-phone: {}", 
                        LoggingUtil.maskPhoneNumber(phoneNumber));
            } catch (Exception e) {
                log.warn("Failed to reset Redis rate limit", e);
            }
        }
        
        // Reset in-memory
        inMemoryStore.remove(phoneNumber);
        log.info("In-memory rate limit counter reset for masked-phone: {}", 
                LoggingUtil.maskPhoneNumber(phoneNumber));
    }
    
    /**
     * Manually trigger Redis reconnection attempt.
     */
    public void attemptRedisReconnection() {
        redisAvailable = true;
        log.info("Manual Redis reconnection triggered");
    }
    
    /**
     * Gets the current circuit breaker state.
     *
     * @return true if Redis is available, false if using in-memory fallback
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }
    
    /**
     * Internal class to hold rate limit state for in-memory storage.
     */
    private static class RateLimitEntry {
        AtomicInteger count;
        long windowStartTime;
        
        RateLimitEntry(int initialCount, long startTime) {
            this.count = new AtomicInteger(initialCount);
            this.windowStartTime = startTime;
        }
    }
}

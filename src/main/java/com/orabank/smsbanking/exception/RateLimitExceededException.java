package com.orabank.smsbanking.exception;

/**
 * Exception thrown when a rate limit is exceeded.
 */
public class RateLimitExceededException extends RuntimeException {
    
    /**
     * The number of remaining requests allowed.
     */
    private final int remainingRequests;
    
    /**
     * The time in seconds until the rate limit resets.
     */
    private final long retryAfterSeconds;
    
    /**
     * Constructs a new RateLimitExceededException with the specified detail message.
     *
     * @param message the detail message
     */
    public RateLimitExceededException(String message) {
        this(message, 0, 60);
    }
    
    /**
     * Constructs a new RateLimitExceededException with the specified detail message and retry information.
     *
     * @param message the detail message
     * @param remainingRequests the number of remaining requests (usually 0)
     * @param retryAfterSeconds the time in seconds until the rate limit resets
     */
    public RateLimitExceededException(String message, int remainingRequests, long retryAfterSeconds) {
        super(message);
        this.remainingRequests = remainingRequests;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    /**
     * Gets the number of remaining requests allowed.
     *
     * @return the remaining requests count
     */
    public int getRemainingRequests() {
        return remainingRequests;
    }
    
    /**
     * Gets the time in seconds until the rate limit resets.
     *
     * @return the retry after seconds
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

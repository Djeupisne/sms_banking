package com.orabank.smsbanking.exception;

/**
 * Exception thrown when a client is not found in the system.
 */
public class ClientNotFoundException extends RuntimeException {
    
    /**
     * Constructs a new ClientNotFoundException with the specified detail message.
     *
     * @param message the detail message
     */
    public ClientNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new ClientNotFoundException with the specified phone number.
     *
     * @param phoneNumber the phone number that was not found
     */
    public ClientNotFoundException(String phoneNumber, boolean masked) {
        super("Client not found for phone number: " + phoneNumber);
    }
    
    /**
     * Constructs a new ClientNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ClientNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

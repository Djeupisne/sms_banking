package com.orabank.smsbanking.gateway;

/**
 * Interface defining the contract for SMS gateway implementations.
 * Provides methods for sending SMS messages through different providers.
 */
public interface SmsGateway {
    
    /**
     * Sends an SMS message to the specified phone number.
     *
     * @param to the recipient's phone number
     * @param message the message content to send
     * @return true if the message was sent successfully, false otherwise
     */
    boolean sendSms(String to, String message);
    
    /**
     * Gets the name/provider of this SMS gateway.
     *
     * @return the gateway name
     */
    String getProviderName();
    
    /**
     * Checks if this SMS gateway is currently available.
     *
     * @return true if the gateway is available, false otherwise
     */
    boolean isAvailable();
}

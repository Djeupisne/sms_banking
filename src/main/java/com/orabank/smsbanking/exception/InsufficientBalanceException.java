package com.orabank.smsbanking.exception;

/**
 * Exception thrown when an account has insufficient balance for a transaction.
 * Message sécurisé ne révélant pas d'informations sensibles sur le solde.
 */
public class InsufficientBalanceException extends RuntimeException {
    
    /**
     * Constructs a new InsufficientBalanceException with a standard message.
     * Ne révèle pas le solde actuel pour des raisons de sécurité.
     */
    public InsufficientBalanceException() {
        super("Solde insuffisant pour effectuer cette opération.");
    }
    
    /**
     * Constructs a new InsufficientBalanceException with the specified detail message.
     * Utiliser uniquement pour les logs internes, pas pour exposer le solde.
     *
     * @param message the detail message (pour logs internes)
     */
    public InsufficientBalanceException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new InsufficientBalanceException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public InsufficientBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}

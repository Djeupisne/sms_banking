package com.orabank.smsbanking.exception;

import com.orabank.smsbanking.dto.response.SmsResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the SMS banking application.
 * Handles common exceptions and provides consistent error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions for request DTOs.
     *
     * @param ex the MethodArgumentNotValidException
     * @return ResponseEntity with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation Failed",
                "message", "Request validation failed",
                "errors", errors
        );

        log.warn("Validation error: {}", errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles client not found exceptions.
     *
     * @param ex the ClientNotFoundException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(ClientNotFoundException.class)
    public ResponseEntity<SmsResponseDto> handleClientNotFoundException(ClientNotFoundException ex) {
        log.warn("Client not found: {}", ex.getMessage());
        SmsResponseDto response = new SmsResponseDto();
        response.setMessage("ORABANK - Client non trouvé. Vérifiez votre numéro de téléphone.");
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles insufficient balance exceptions.
     * Returns a professional error message without revealing internal details.
     *
     * @param ex the InsufficientBalanceException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<SmsResponseDto> handleInsufficientBalanceException(InsufficientBalanceException ex) {
        log.warn("Solde insuffisant: {}", ex.getMessage());
        SmsResponseDto response = new SmsResponseDto();
        response.setMessage("ORABANK - Solde insuffisant. Votre solde actuel ne permet pas ce virement.");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles rate limit exceeded exceptions.
     *
     * @param ex the RateLimitExceededException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<SmsResponseDto> handleRateLimitExceededException(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        SmsResponseDto response = new SmsResponseDto();
        response.setMessage("ORABANK - Trop de requêtes. Veuillez réessayer dans 1 minute.");
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Handles generic exceptions.
     *
     * @param ex the Exception
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SmsResponseDto> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        SmsResponseDto response = new SmsResponseDto();
        response.setMessage("ORABANK - Erreur technique. Veuillez réessayer.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
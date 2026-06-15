package com.orabank.smsbanking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration class for enabling asynchronous task execution.
 * Enables the @Async annotation support across the application.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // This configuration enables asynchronous method execution
    // Methods annotated with @Async will run in separate threads
}

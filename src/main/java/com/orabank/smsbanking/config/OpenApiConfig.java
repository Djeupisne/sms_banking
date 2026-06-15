package com.orabank.smsbanking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI documentation setup.
 * Configures Swagger UI and API documentation.
 */
@Configuration
public class OpenApiConfig {
    
    /**
     * Creates and configures the OpenAPI specification with security schemes.
     *
     * @return the configured OpenAPI instance
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ORABANK SMS Banking API")
                .version("1.0.0")
                .description("API for ORABANK SMS Banking Services")
                .contact(new Contact()
                    .name("ORABANK Development Team")
                    .email("api@orabank.com")
                    .url("https://www.orabank.com")))
            .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
            .schemaRequirement("basicAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization"));
    }
    
    /**
     * Configures OpenAPI to use security schemes for authentication.
     * This enables the "Authorize" button in Swagger UI.
     * The customOpenAPI bean already handles the security configuration.
     */
}

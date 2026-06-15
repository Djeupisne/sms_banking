package com.orabank.smsbanking.config;

import com.orabank.smsbanking.entity.User;
import com.orabank.smsbanking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final UserRepository userRepository;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:#{null}}")
    private String adminPassword;

    @Value("${app.frontend.url:http://localhost:3001}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        MvcRequestMatcher.Builder mvcMatcherBuilder = new MvcRequestMatcher.Builder(introspector);

        // ✅ Configuration de l'entry point personnalisé
        BasicAuthenticationEntryPoint authenticationEntryPoint = new BasicAuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                 org.springframework.security.core.AuthenticationException authException) throws IOException {
                response.setContentType("text/plain;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                String message = "Nom d'utilisateur ou mot de passe incorrect";

                // Vérifier le message d'exception
                if (authException != null && authException.getMessage() != null) {
                    String exceptionMessage = authException.getMessage();
                    if (exceptionMessage.contains("désactivé") || exceptionMessage.contains("disabled")) {
                        message = "Votre compte a été désactivé. Veuillez contacter l'administrateur.";
                    } else if (authException.getCause() instanceof DisabledException) {
                        message = "Votre compte a été désactivé. Veuillez contacter l'administrateur.";
                    }
                }

                log.info("Authentication error: {}", message);
                response.getWriter().write(message);
            }

            @Override
            public void afterPropertiesSet() {
                setRealmName("Orabank");
                super.afterPropertiesSet();
            }
        };

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(mvcMatcherBuilder.pattern("/health")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/health/**")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/v1/health/**")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/sms/webhook")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/auth/forgot-password")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/auth/reset-password")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/auth/reset-password/validate")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/auth/**")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/swagger-ui/**")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/v3/api-docs/**")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/webjars/**")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/actuator/health")).permitAll()
                        .requestMatchers(mvcMatcherBuilder.pattern("/actuator/info")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/h2-console/**")).permitAll()

                        .requestMatchers(mvcMatcherBuilder.pattern("/api/admin/**")).hasRole("ADMIN")
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/v1/admin/**")).hasRole("ADMIN")
                        .requestMatchers(mvcMatcherBuilder.pattern("/actuator/**")).hasRole("ADMIN")

                        .requestMatchers(mvcMatcherBuilder.pattern("/api/transactions/**")).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/clients/**")).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/sms/logs/**")).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/statistics/**")).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(mvcMatcherBuilder.pattern("/api/transfers/**")).hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable())
                        .contentTypeOptions(configurer -> {})
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", "DENY"))
                        .addHeaderWriter(new StaticHeadersWriter("X-XSS-Protection", "1; mode=block"))
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ✅ Correction : Utilise la variable d'environnement pour l'origine autorisée
        log.info("Configuration CORS pour l'origine: {}", frontendUrl);
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Primary
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return username -> {
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null) {
                if (!user.isActive()) {
                    log.warn("Tentative de connexion sur compte désactivé: {}", username);
                    throw new DisabledException("Votre compte a été désactivé. Veuillez contacter l'administrateur.");
                }

                log.info("Authentification réussie - Utilisateur actif: {}", username);

                return org.springframework.security.core.userdetails.User.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                        .build();
            }

            if (username.equals(adminUsername)) {
                log.info("Authentification admin en mémoire: {}", adminUsername);
                return org.springframework.security.core.userdetails.User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build();
            }

            log.warn("Utilisateur non trouvé: {}", username);
            throw new UsernameNotFoundException("Nom d'utilisateur ou mot de passe incorrect");
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService(passwordEncoder));
        authProvider.setPasswordEncoder(passwordEncoder);
        authProvider.setHideUserNotFoundExceptions(false);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
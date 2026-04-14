package com.carpool.web.config;

import com.carpool.web.filter.JwtAuthFilter;
import com.carpool.web.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT-based security configuration.
 *
 * Public endpoints (no token required):
 *   POST /api/v1/auth/telegram   — login entry point
 *   GET  /api/v1/hubs            — hub list (needed before login for ride creation UX)
 *   GET  /actuator/health        — health check for Docker/load balancer
 *
 * All other endpoints require a valid JWT.
 *
 * @EnableMethodSecurity enables @PreAuthorize at method level
 * for finer-grained role checks beyond just "authenticated".
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter   jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF — stateless REST API, no browser sessions
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless — no HttpSession created or used
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Disable default form login and HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/telegram").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/v1/hubs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Swagger UI — local only (disabled in prod via properties)
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Static files — test pages
                        .requestMatchers("/telegram-login-test.html").permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Rate limiter runs before JWT filter
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                // JWT filter runs before Spring's auth filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}

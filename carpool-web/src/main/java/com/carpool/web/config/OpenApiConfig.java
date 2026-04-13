package com.carpool.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * Defines:
 *   - API metadata (title, version, description)
 *   - JWT Bearer auth scheme — allows testing protected endpoints
 *     directly in Swagger UI without Postman
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI carpoolOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Car-e-pool PH API")
                        .description("""
                                REST API backend for the Carpool PH platform.
                                
                                **Authentication:**
                                1. Use `POST /api/v1/auth/telegram` to get a JWT token
                                2. Click **Authorize** button above
                                3. Enter: `Bearer <your_token>`
                                4. All protected endpoints will use the token automatically
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("""
                                        Car-e-pool PH API
                                        Telegram: @AspectJump
                                        """)
                                .email("aspectjump.java@gmail.com")))
                // Global JWT security requirement —
                // shows the lock icon on all protected endpoints
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token here. " +
                                                "Get it from POST /api/v1/auth/telegram")));
    }
}
package com.phu.ecommerceapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI ecommerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking-Grade E-Commerce API")
                        .version("0.0.1")
                        .description("""
                                Secure commerce and payments API with OAuth2 resource-server security, idempotent
                                payment/refund workflows, immutable ledger records, tamper-evident audit events,
                                reconciliation checks, and outbox-backed stock updates.
                                """)
                        .license(new License().name("Portfolio project")))
                .components(new Components().addSecuritySchemes(BEARER_JWT, bearerJwtScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }

    private SecurityScheme bearerJwtScheme() {
        return new SecurityScheme()
                .name(BEARER_JWT)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Keycloak-issued OAuth2 access token with role and scope authorities.");
    }
}

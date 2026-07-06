package com.phu.ecommerceapi.identity.infrastructure;

import com.phu.ecommerceapi.identity.application.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringSecurityCurrentUserProviderTest {

    private final SpringSecurityCurrentUserProvider provider = new SpringSecurityCurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsEmptyWhenNoAuthenticatedJwtExists() {
        assertThat(provider.getCurrentUser()).isEmpty();
    }

    @Test
    void requiresAuthenticatedJwt() {
        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated user is required");
    }

    @Test
    void mapsJwtAuthenticationIntoCurrentUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(claims -> claims.putAll(Map.of(
                        "preferred_username", "customer@example.com",
                        "email", "customer@example.com"
                )))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_catalog:read"),
                        new SimpleGrantedAuthority("SCOPE_checkout:write")
                ),
                "customer@example.com"
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CurrentUser currentUser = provider.requireCurrentUser();

        assertThat(currentUser.subject()).isEqualTo("subject-123");
        assertThat(currentUser.username()).isEqualTo("customer@example.com");
        assertThat(currentUser.email()).isEqualTo("customer@example.com");
        assertThat(currentUser.roles()).containsExactlyInAnyOrder("customer", "admin");
        assertThat(currentUser.scopes()).containsExactlyInAnyOrder("catalog:read", "checkout:write");
    }
}

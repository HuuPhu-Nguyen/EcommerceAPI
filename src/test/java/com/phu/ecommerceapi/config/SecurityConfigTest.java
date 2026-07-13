package com.phu.ecommerceapi.config;

import com.phu.ecommerceapi.payment.api.FakeProviderWebhookController;
import com.phu.ecommerceapi.payment.api.StripeProviderWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/customer-profiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesAllowAnonymousRequests() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void operationalActuatorEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operationalActuatorEndpointsRejectCustomerTokens() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void operationalActuatorEndpointsAllowOpsTokens() throws Exception {
        mockMvc.perform(get("/actuator/info").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointAcceptsJwtAuthenticationAndReachesController() throws Exception {
        mockMvc.perform(get("/products/{id}", -1).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void exactFakeWebhookPostPathIsAnonymous() throws Exception {
        mockMvc.perform(post("/payments/provider-webhooks/fake")
                        .header(FakeProviderWebhookController.WEBHOOK_SECRET_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("evt-security-fake-anonymous")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exactStripeWebhookPostPathIsAnonymous() throws Exception {
        mockMvc.perform(post("/payments/provider-webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("evt-security-stripe-protected")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownWebhookPostPathRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/payments/provider-webhooks/unknown")
                        .header(StripeProviderWebhookController.STRIPE_SIGNATURE_HEADER, "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("evt-security-unknown-protected")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerEndpointIsRemoved() throws Exception {
        mockMvc.perform(post("/register").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void jwtConverterMapsKeycloakRealmAndClientRoles() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("customer-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of("customer")))
                .claim("resource_access", Map.of(
                        "ecommerce-api", Map.of("roles", List.of("admin"))
                ))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void jwtConverterDoesNotMapRolesFromOtherResourceClients() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("customer-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("resource_access", Map.of(
                        "other-client", Map.of("roles", List.of("admin"))
                ))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void jwtConverterMapsOnlyConfiguredResourceClientRolesWhenMultipleClientsArePresent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("customer-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("resource_access", Map.of(
                        "ecommerce-api", Map.of("roles", List.of("customer")),
                        "other-client", Map.of("roles", List.of("admin"))
                ))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_CUSTOMER")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void jwtConverterKeepsScopeAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("customer-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", "cart:read payment:create")
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("SCOPE_cart:read", "SCOPE_payment:create");
    }

    private String webhookBody(String eventId) {
        return """
                {
                  "eventId": "%s",
                  "type": "payment.succeeded",
                  "paymentId": "00000000-0000-0000-0000-000000000001",
                  "providerPaymentId": "fake_security_payment"
                }
                """.formatted(eventId);
    }
}

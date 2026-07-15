package com.phu.ecommerceapi.config;

import com.phu.ecommerceapi.identity.application.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpenApiExposureSecurityTest.OpenApiDocsStubController.class)
@Import({
        SecurityConfig.class,
        OpenApiExposureSecurityTest.OpenApiDocsStubController.class
})
@TestPropertySource(properties = {
        "app.openapi.public-docs-enabled=false",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "app.security.oauth2.required-audience=ecommerce-api",
        "app.security.oauth2.resource-client-id=ecommerce-api",
        "app.security.oauth2.allowed-authorized-parties=ecommerce-web",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused-jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/test"
})
class OpenApiExposureSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void privateDocsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateDocsAllowAdminAndAuditorUsers() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void privateDocsRejectCustomerUsers() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void prodProfileDisablesSpringDocAndPublicDocsByDefault() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "ECOMMERCE_DB_URL=jdbc:postgresql://localhost:5432/ecommerce",
                        "ECOMMERCE_DB_USERNAME=ecommerce",
                        "ECOMMERCE_DB_PASSWORD=ecommerce",
                        "OAUTH2_REQUIRED_AUDIENCE=ecommerce-api",
                        "OAUTH2_RESOURCE_CLIENT_ID=ecommerce-api",
                        "OAUTH2_ALLOWED_AUTHORIZED_PARTIES=ecommerce-web",
                        "AUDIT_SIGNATURE_SECRET=prod-audit-signature-secret",
                        "OAUTH2_ISSUER_URI=http://localhost/realms/test"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment()
                            .getProperty("app.openapi.public-docs-enabled", Boolean.class)).isFalse();
                    assertThat(context.getEnvironment()
                            .getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
                    assertThat(context.getEnvironment()
                            .getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
                });
    }

    @RestController
    static class OpenApiDocsStubController {

        @GetMapping("/v3/api-docs")
        Map<String, String> docs() {
            return Map.of("openapi", "3.1.0");
        }
    }
}

package com.phu.ecommerceapi.config;

import com.phu.ecommerceapi.payment.api.FakeProviderWebhookController;
import com.phu.ecommerceapi.payment.application.FakeProviderWebhookUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.payment-provider.active=stripe",
        "app.payment-provider.enabled=stripe",
        "app.stripe.secret-key=sk_test_safe_placeholder",
        "app.stripe.webhook-secret=whsec_test_safe_placeholder"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FakeProviderWebhookDisabledSecurityTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fakeWebhookControllerAndUseCaseAreNotRegisteredWhenFakeProviderIsDisabled() {
        assertThat(applicationContext.getBeansOfType(FakeProviderWebhookController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(FakeProviderWebhookUseCase.class)).isEmpty();
    }

    @Test
    void disabledFakeWebhookRouteReturnsNotFoundForAnonymousRequests() throws Exception {
        mockMvc.perform(post("/payments/provider-webhooks/fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fakeWebhookBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void stripeWebhookRouteRemainsAnonymousWhenFakeProviderIsDisabled() throws Exception {
        mockMvc.perform(post("/payments/provider-webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void prodProfileDoesNotDefaultToFakeProvider() throws IOException {
        String prodProperties = Files.readString(Path.of("src/main/resources/application-prod.properties"));

        assertThat(prodProperties)
                .contains("app.payment-provider.enabled=${PAYMENT_PROVIDER_ENABLED:}")
                .doesNotContain("app.payment-provider.enabled=${PAYMENT_PROVIDER_ENABLED:fake}");
    }

    private String fakeWebhookBody() {
        return """
                {
                  "eventId": "evt-disabled-fake-route",
                  "type": "payment.succeeded",
                  "paymentId": "00000000-0000-0000-0000-000000000001",
                  "providerPaymentId": "fake_disabled_payment"
                }
                """;
    }
}

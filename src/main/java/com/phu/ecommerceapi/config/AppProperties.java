package com.phu.ecommerceapi.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String environment,
        @NotBlank String paymentProvider,
        @NotBlank String authenticationProvider,
        FakeProvider fakeProvider
) {
    public AppProperties {
        if (fakeProvider == null) {
            fakeProvider = new FakeProvider("");
        }
    }

    @AssertTrue(message = "app.payment-provider must be fake until an external provider adapter is implemented")
    public boolean isSupportedPaymentProvider() {
        return isFakeProvider();
    }

    @AssertTrue(message = "app.fake-provider.webhook-secret is required when app.payment-provider=fake")
    public boolean isFakeProviderWebhookSecretConfiguredWhenRequired() {
        return !isFakeProvider() || !fakeProvider.webhookSecret().isBlank();
    }

    public boolean isFakeProvider() {
        return "fake".equalsIgnoreCase(paymentProvider);
    }

    public record FakeProvider(String webhookSecret) {
        public FakeProvider {
            if (webhookSecret == null) {
                webhookSecret = "";
            }
        }
    }
}

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
        Stripe stripe,
        FakeProvider fakeProvider
) {
    public AppProperties {
        if (stripe == null) {
            stripe = new Stripe("");
        }
        if (fakeProvider == null) {
            fakeProvider = new FakeProvider("");
        }
    }

    @AssertTrue(message = "app.stripe.secret-key is required when app.payment-provider=stripe")
    public boolean isStripeSecretConfiguredWhenRequired() {
        return !isStripeProvider() || !stripe.secretKey().isBlank();
    }

    public boolean isStripeProvider() {
        return "stripe".equalsIgnoreCase(paymentProvider);
    }

    @AssertTrue(message = "app.fake-provider.webhook-secret is required when app.payment-provider=fake")
    public boolean isFakeProviderWebhookSecretConfiguredWhenRequired() {
        return !isFakeProvider() || !fakeProvider.webhookSecret().isBlank();
    }

    public boolean isFakeProvider() {
        return "fake".equalsIgnoreCase(paymentProvider);
    }

    public record Stripe(String secretKey) {
        public Stripe {
            if (secretKey == null) {
                secretKey = "";
            }
        }
    }

    public record FakeProvider(String webhookSecret) {
        public FakeProvider {
            if (webhookSecret == null) {
                webhookSecret = "";
            }
        }
    }
}

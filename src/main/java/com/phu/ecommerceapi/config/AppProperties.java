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
        Stripe stripe
) {
    public AppProperties {
        if (stripe == null) {
            stripe = new Stripe("");
        }
    }

    @AssertTrue(message = "app.stripe.secret-key is required when app.payment-provider=stripe")
    public boolean isStripeSecretConfiguredWhenRequired() {
        return !isStripeProvider() || !stripe.secretKey().isBlank();
    }

    public boolean isStripeProvider() {
        return "stripe".equalsIgnoreCase(paymentProvider);
    }

    public record Stripe(String secretKey) {
        public Stripe {
            if (secretKey == null) {
                secretKey = "";
            }
        }
    }
}

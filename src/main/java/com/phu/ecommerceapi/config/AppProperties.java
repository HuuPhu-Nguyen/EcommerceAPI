package com.phu.ecommerceapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String environment,
        @NotBlank String authenticationProvider,
        @Valid PaymentProviderProperties paymentProvider,
        @Valid FakeProvider fakeProvider,
        @Valid StripeProviderProperties stripe
) {
    public AppProperties {
        environment = normalizeText(environment);
        authenticationProvider = normalizeText(authenticationProvider);
        if (paymentProvider == null) {
            paymentProvider = new PaymentProviderProperties("", List.of());
        }
        if (fakeProvider == null) {
            fakeProvider = new FakeProvider("");
        }
        if (stripe == null) {
            stripe = new StripeProviderProperties("", "", "", 2000, 5000);
        }
    }

    @AssertTrue(message = "app.payment-provider.active must be included in app.payment-provider.enabled")
    public boolean isPaymentProviderActiveEnabled() {
        if (paymentProvider.active().isBlank() || paymentProvider.enabled().isEmpty()) {
            return true;
        }
        return paymentProvider.enabled().contains(paymentProvider.active());
    }

    @AssertTrue(
            message = "app.fake-provider.webhook-secret is required when "
                    + "app.payment-provider.enabled contains fake"
    )
    public boolean isFakeProviderWebhookSecretConfiguredWhenRequired() {
        return !paymentProvider.isEnabled("fake") || !fakeProvider.webhookSecret().isBlank();
    }

    @AssertTrue(
            message = "app.stripe.secret-key is required when "
                    + "app.payment-provider.enabled contains stripe"
    )
    public boolean isStripeSecretKeyConfiguredWhenRequired() {
        return !paymentProvider.isEnabled("stripe") || !stripe.secretKey().isBlank();
    }

    @AssertTrue(
            message = "app.stripe.webhook-secret is required when "
                    + "app.payment-provider.enabled contains stripe"
    )
    public boolean isStripeWebhookSecretConfiguredWhenRequired() {
        return !paymentProvider.isEnabled("stripe") || !stripe.webhookSecret().isBlank();
    }

    public record PaymentProviderProperties(
            @NotBlank String active,
            @NotEmpty List<String> enabled
    ) {
        private static final Set<String> SUPPORTED_PROVIDER_CODES = Set.of("fake", "stripe");

        public PaymentProviderProperties {
            active = normalizeProviderCode(active);
            enabled = normalizeEnabled(enabled);
        }

        @AssertTrue(message = "app.payment-provider.active must be one of fake,stripe")
        public boolean isActiveProviderCodeSupported() {
            return active.isBlank() || SUPPORTED_PROVIDER_CODES.contains(active);
        }

        @AssertTrue(message = "app.payment-provider.enabled must contain only fake,stripe and no blank provider codes")
        public boolean isEnabledProviderCodesSupported() {
            return !enabled.isEmpty()
                    && enabled.stream().allMatch(code -> !code.isBlank() && SUPPORTED_PROVIDER_CODES.contains(code));
        }

        public boolean isEnabled(String providerCode) {
            return enabled.contains(normalizeProviderCode(providerCode));
        }

        private static List<String> normalizeEnabled(List<String> values) {
            if (values == null) {
                return List.of();
            }

            Set<String> normalized = new LinkedHashSet<>();
            for (String value : values) {
                for (String token : splitProviderCodes(value)) {
                    normalized.add(normalizeProviderCode(token));
                }
            }
            return List.copyOf(normalized);
        }

        private static List<String> splitProviderCodes(String value) {
            if (value == null) {
                return List.of("");
            }

            String[] tokens = value.split(",");
            List<String> result = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                result.add(token);
            }
            return result;
        }
    }

    public record FakeProvider(String webhookSecret) {
        public FakeProvider {
            webhookSecret = normalizeText(webhookSecret);
        }
    }

    public record StripeProviderProperties(
            String secretKey,
            String webhookSecret,
            String apiVersion,
            @Min(1) @Max(10000) int connectTimeoutMs,
            @Min(1) @Max(30000) int readTimeoutMs
    ) {
        public StripeProviderProperties {
            secretKey = normalizeText(secretKey);
            webhookSecret = normalizeText(webhookSecret);
            apiVersion = normalizeOptionalValue(apiVersion);
        }

        @AssertTrue(message = "app.stripe.api-version must be nonblank when set")
        public boolean isApiVersionNonblankWhenSet() {
            return apiVersion.isEmpty() || !apiVersion.isBlank();
        }

        private static String normalizeOptionalValue(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }

            String normalized = value.trim();
            return normalized.isEmpty() ? value : normalized;
        }
    }

    private static String normalizeProviderCode(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}

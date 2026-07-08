package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.infrastructure.FakePaymentProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRegistryTest {

    @Test
    void resolvesFakeWhenFakeIsOnlyEnabledProviderAndRequestOmitsProvider() {
        FakePaymentProvider fakeProvider = new FakePaymentProvider();
        PaymentProviderRegistry registry = registry(
                properties("fake", List.of("fake")),
                List.of(fakeProvider)
        );

        assertThat(registry.resolveForPayment(null)).isSameAs(fakeProvider);
    }

    @Test
    void requiresProviderWhenMultipleProvidersAreEnabled() {
        PaymentProviderRegistry registry = registry(
                properties("fake", List.of("fake", "stripe")),
                List.of(new FakePaymentProvider(), provider("stripe"))
        );

        assertThatThrownBy(() -> registry.resolveForPayment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider is required when multiple payment providers are enabled");
    }

    @Test
    void rejectsDisabledProviderRequests() {
        PaymentProviderRegistry registry = registry(
                properties("fake", List.of("fake")),
                List.of(new FakePaymentProvider(), provider("stripe"))
        );

        assertThatThrownBy(() -> registry.resolveForPayment("stripe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment provider is disabled: stripe");
    }

    @Test
    void rejectsUnknownProviderRequests() {
        PaymentProviderRegistry registry = registry(
                properties("fake", List.of("fake")),
                List.of(new FakePaymentProvider())
        );

        assertThatThrownBy(() -> registry.resolveForPayment("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown payment provider: unknown");
    }

    @Test
    void rejectsDuplicateProviderBeanCodes() {
        AppProperties properties = properties("fake", List.of("fake"));

        assertThatThrownBy(() -> registry(
                properties,
                List.of(new FakePaymentProvider(), provider("fake"))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate payment provider bean code: fake");
    }

    @Test
    void rejectsEnabledProviderWithNoRegisteredBean() {
        assertThatThrownBy(() -> registry(
                properties("fake", List.of("fake", "stripe")),
                List.of(new FakePaymentProvider())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Enabled payment provider has no registered bean: stripe");
    }

    @Test
    void exposesEnabledProviderCapabilitiesOnly() {
        PaymentProviderRegistry registry = registry(
                properties("fake", List.of("fake")),
                List.of(new FakePaymentProvider(), provider("stripe"))
        );

        assertThat(registry.enabledProviderCapabilities())
                .extracting(PaymentProviderCapabilitiesView::providerCode)
                .containsExactly("fake");
    }

    private PaymentProviderRegistry registry(AppProperties properties, List<PaymentProvider> providers) {
        return new PaymentProviderRegistry(providers, properties);
    }

    private AppProperties properties(String activeProvider, List<String> enabledProviders) {
        return new AppProperties(
                "test",
                "keycloak",
                new AppProperties.PaymentProviderProperties(activeProvider, enabledProviders),
                new AppProperties.FakeProvider("fake-webhook-secret")
        );
    }

    private PaymentProvider provider(String providerCode) {
        return new TestPaymentProvider(providerCode);
    }

    private record TestPaymentProvider(String providerCode) implements PaymentProvider {

        @Override
        public PaymentProviderCapabilities capabilities() {
            return new PaymentProviderCapabilities(
                    Set.of("USD"),
                    new BigDecimal("0.50"),
                    new BigDecimal("999999.99"),
                    true,
                    true,
                    null
            );
        }

        @Override
        public PaymentProviderResult createPayment(PaymentProviderRequest request) {
            return PaymentProviderResult.succeeded(providerCode + "_payment", "approved");
        }

        @Override
        public PaymentRefundProviderResult refundPayment(PaymentRefundProviderRequest request) {
            return PaymentRefundProviderResult.succeeded(providerCode + "_refund", "approved");
        }
    }
}

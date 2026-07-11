package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentProviderCapabilitiesView;
import com.phu.ecommerceapi.payment.application.PaymentProviderRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("paymentProvidersHealthIndicator")
public class PaymentProvidersHealthIndicator implements HealthIndicator {

    private final PaymentProviderRegistry paymentProviderRegistry;

    public PaymentProvidersHealthIndicator(PaymentProviderRegistry paymentProviderRegistry) {
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    @Override
    public Health health() {
        List<PaymentProviderCapabilitiesView> capabilities = paymentProviderRegistry.enabledProviderCapabilities();
        Health.Builder builder = capabilities.stream().allMatch(PaymentProviderCapabilitiesView::available)
                ? Health.up()
                : Health.down();
        return builder.withDetail("providers", providerDetails(capabilities)).build();
    }

    private List<Map<String, Object>> providerDetails(List<PaymentProviderCapabilitiesView> capabilities) {
        return capabilities.stream()
                .map(provider -> Map.<String, Object>of(
                        "provider", provider.providerCode(),
                        "available", provider.available(),
                        "supportsPayments", provider.supportsPayments(),
                        "supportsRefunds", provider.supportsRefunds(),
                        "unavailableReason", provider.unavailableReason() == null
                                ? ""
                                : provider.unavailableReason()
                ))
                .toList();
    }
}

package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PaymentProviderRegistry {

    private final AppProperties.PaymentProviderProperties configuredProviders;
    private final Map<String, PaymentProvider> providersByCode;

    public PaymentProviderRegistry(List<PaymentProvider> providers, AppProperties appProperties) {
        this.configuredProviders = appProperties.paymentProvider();
        this.providersByCode = indexProviders(providers);
        validateConfiguredProviders();
    }

    public PaymentProvider resolveForPayment(String requestedProviderCode) {
        if (requestedProviderCode == null || requestedProviderCode.isBlank()) {
            return resolveDefaultProvider();
        }

        String providerCode = normalizeProviderCode(requestedProviderCode);
        if (!providersByCode.containsKey(providerCode)) {
            throw new IllegalArgumentException("Unknown payment provider: " + providerCode);
        }
        if (!isProviderEnabled(providerCode)) {
            throw new IllegalArgumentException("Payment provider is disabled: " + providerCode);
        }
        return providersByCode.get(providerCode);
    }

    public PaymentProvider resolveExistingProvider(String providerCode) {
        String normalizedCode = normalizeProviderCode(providerCode);
        PaymentProvider provider = providersByCode.get(normalizedCode);
        if (provider == null) {
            throw new IllegalStateException("Payment provider has no registered bean: " + normalizedCode);
        }
        if (!isProviderEnabled(normalizedCode)) {
            throw new IllegalStateException("Payment provider is not enabled: " + normalizedCode);
        }
        return provider;
    }

    public List<PaymentProviderCapabilitiesView> enabledProviderCapabilities() {
        return configuredProviders.enabled()
                .stream()
                .map(providersByCode::get)
                .map(PaymentProviderCapabilitiesView::from)
                .toList();
    }

    public boolean isProviderEnabled(String providerCode) {
        return configuredProviders.isEnabled(providerCode);
    }

    private PaymentProvider resolveDefaultProvider() {
        List<String> enabledProviders = configuredProviders.enabled();
        if (enabledProviders.size() == 1) {
            return providersByCode.get(enabledProviders.getFirst());
        }
        throw new IllegalArgumentException("provider is required when multiple payment providers are enabled");
    }

    private Map<String, PaymentProvider> indexProviders(List<PaymentProvider> providers) {
        Map<String, PaymentProvider> indexedProviders = new LinkedHashMap<>();
        for (PaymentProvider provider : providers) {
            String providerCode = normalizeProviderCode(provider.providerCode());
            if (providerCode.isBlank()) {
                throw new IllegalStateException("Payment provider bean returned a blank provider code");
            }
            PaymentProvider previousProvider = indexedProviders.putIfAbsent(providerCode, provider);
            if (previousProvider != null) {
                throw new IllegalStateException("Duplicate payment provider bean code: " + providerCode);
            }
        }
        return Map.copyOf(indexedProviders);
    }

    private void validateConfiguredProviders() {
        for (String providerCode : configuredProviders.enabled()) {
            if (!providersByCode.containsKey(providerCode)) {
                throw new IllegalStateException("Enabled payment provider has no registered bean: " + providerCode);
            }
        }

        String activeProvider = configuredProviders.active();
        if (!providersByCode.containsKey(activeProvider)) {
            throw new IllegalStateException("Active payment provider has no registered bean: " + activeProvider);
        }
    }

    private String normalizeProviderCode(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }
}

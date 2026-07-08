package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.Set;

public record PaymentProviderCapabilitiesView(
        String providerCode,
        Set<String> supportedCurrencies,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        boolean supportsPayments,
        boolean supportsRefunds,
        boolean available,
        String unavailableReason
) {

    public static PaymentProviderCapabilitiesView from(PaymentProvider provider) {
        PaymentProviderCapabilities capabilities = provider.capabilities();
        return new PaymentProviderCapabilitiesView(
                provider.providerCode(),
                capabilities.supportedCurrencies(),
                capabilities.minimumAmount(),
                capabilities.maximumAmount(),
                capabilities.supportsPayments(),
                capabilities.supportsRefunds(),
                capabilities.available(),
                capabilities.unavailableReason()
        );
    }
}

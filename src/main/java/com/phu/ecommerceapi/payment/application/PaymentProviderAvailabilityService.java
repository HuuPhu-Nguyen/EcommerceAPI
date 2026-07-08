package com.phu.ecommerceapi.payment.application;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PaymentProviderAvailabilityService {

    private final PaymentProviderRegistry paymentProviderRegistry;

    public PaymentProviderAvailabilityService(PaymentProviderRegistry paymentProviderRegistry) {
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    public List<String> allowedProviderCodes(BigDecimal amount, String currency) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String normalizedCurrency = normalizeCurrency(currency);

        return paymentProviderRegistry.enabledProviderCapabilities()
                .stream()
                .filter(capabilities -> supportsPayment(capabilities, normalizedAmount, normalizedCurrency))
                .map(PaymentProviderCapabilitiesView::providerCode)
                .toList();
    }

    private boolean supportsPayment(
            PaymentProviderCapabilitiesView capabilities,
            BigDecimal amount,
            String currency
    ) {
        return capabilities.available()
                && capabilities.supportsPayments()
                && capabilities.supportedCurrencies().contains(currency)
                && amount.compareTo(capabilities.minimumAmount()) >= 0
                && amount.compareTo(capabilities.maximumAmount()) <= 0;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "payment provider availability amount is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("payment provider availability amount must not be negative");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("payment provider availability currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}

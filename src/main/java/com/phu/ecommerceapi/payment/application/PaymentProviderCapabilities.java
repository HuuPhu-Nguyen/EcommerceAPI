package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record PaymentProviderCapabilities(
        Set<String> supportedCurrencies,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        boolean supportsRefunds,
        boolean available,
        String unavailableReason
) {

    public PaymentProviderCapabilities {
        if (supportedCurrencies == null || supportedCurrencies.isEmpty()) {
            throw new IllegalArgumentException("Supported currencies are required");
        }
        supportedCurrencies = normalizeCurrencies(supportedCurrencies);

        Objects.requireNonNull(minimumAmount, "minimum amount is required");
        Objects.requireNonNull(maximumAmount, "maximum amount is required");
        if (minimumAmount.signum() <= 0) {
            throw new IllegalArgumentException("Minimum amount must be positive");
        }
        if (maximumAmount.compareTo(minimumAmount) < 0) {
            throw new IllegalArgumentException("Maximum amount must be greater than or equal to minimum amount");
        }
        minimumAmount = minimumAmount.setScale(2, RoundingMode.UNNECESSARY);
        maximumAmount = maximumAmount.setScale(2, RoundingMode.UNNECESSARY);

        if (unavailableReason != null && unavailableReason.isBlank()) {
            unavailableReason = null;
        }
    }

    private static Set<String> normalizeCurrencies(Set<String> currencies) {
        Set<String> normalized = new TreeSet<>();
        for (String currency : currencies) {
            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException("Supported currency code is required");
            }

            String code = currency.trim().toUpperCase(Locale.ROOT);
            if (code.length() != 3) {
                throw new IllegalArgumentException("Supported currency must be an ISO 4217 code");
            }
            normalized.add(code);
        }
        return Set.copyOf(normalized);
    }
}

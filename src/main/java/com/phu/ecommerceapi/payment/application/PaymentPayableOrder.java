package com.phu.ecommerceapi.payment.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PaymentPayableOrder(
        UUID orderId,
        BigDecimal amount,
        String currency
) {

    public PaymentPayableOrder {
        Objects.requireNonNull(orderId, "payable order id is required");
        Objects.requireNonNull(amount, "payable order amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("payable order amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("payable order currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}

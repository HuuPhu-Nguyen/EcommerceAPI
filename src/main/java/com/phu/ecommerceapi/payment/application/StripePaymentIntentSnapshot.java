package com.phu.ecommerceapi.payment.application;

import java.util.Locale;

public record StripePaymentIntentSnapshot(
        String providerPaymentId,
        String status,
        String failureCode,
        String message
) {

    public StripePaymentIntentSnapshot {
        providerPaymentId = requireText(providerPaymentId, "Stripe PaymentIntent id");
        status = requireText(status, "Stripe PaymentIntent status").toLowerCase(Locale.ROOT);
        failureCode = trimToNull(failureCode);
        message = trimToNull(message);
    }

    public boolean isSucceeded() {
        return "succeeded".equals(status);
    }

    public boolean isFailedOrCanceled() {
        return "requires_payment_method".equals(status) || "canceled".equals(status);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

package com.phu.ecommerceapi.payment.infrastructure;

public record StripePaymentIntentResult(
        String paymentIntentId,
        String status,
        String failureCode
) {

    public StripePaymentIntentResult {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Stripe PaymentIntent id is required");
        }
        paymentIntentId = paymentIntentId.trim();
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Stripe PaymentIntent status is required");
        }
        status = status.trim();
        failureCode = failureCode == null || failureCode.isBlank() ? null : failureCode.trim();
    }
}

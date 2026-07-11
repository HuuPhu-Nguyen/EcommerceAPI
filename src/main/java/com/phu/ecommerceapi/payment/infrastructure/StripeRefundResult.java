package com.phu.ecommerceapi.payment.infrastructure;

public record StripeRefundResult(
        String refundId,
        String status,
        String failureCode
) {

    public StripeRefundResult {
        if (refundId == null || refundId.isBlank()) {
            throw new IllegalArgumentException("Stripe refund id is required");
        }
        refundId = refundId.trim();
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Stripe refund status is required");
        }
        status = status.trim();
        failureCode = failureCode == null || failureCode.isBlank() ? null : failureCode.trim();
    }
}

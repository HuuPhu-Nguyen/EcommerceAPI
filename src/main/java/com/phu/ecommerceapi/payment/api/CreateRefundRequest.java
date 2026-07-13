package com.phu.ecommerceapi.payment.api;

public record CreateRefundRequest(
        String reason
) {
    private static final int MAX_REASON_LENGTH = 500;

    public CreateRefundRequest {
        if (reason == null || reason.isBlank()) {
            reason = "customer_request";
        } else {
            reason = reason.trim();
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("refund reason must be " + MAX_REASON_LENGTH + " characters or fewer");
        }
    }
}

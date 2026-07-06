package com.phu.ecommerceapi.payment.api;

public record CreateRefundRequest(
        String reason
) {

    public CreateRefundRequest {
        if (reason == null || reason.isBlank()) {
            reason = "customer_request";
        } else {
            reason = reason.trim();
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("refund reason must be 500 characters or fewer");
        }
    }
}

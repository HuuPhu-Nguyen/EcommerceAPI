package com.phu.ecommerceapi.payment.application;

public record CreateRefundResult(
        int httpStatus,
        String responseBody
) {

    public CreateRefundResult {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status must be valid");
        }
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("refund response body is required");
        }
    }
}

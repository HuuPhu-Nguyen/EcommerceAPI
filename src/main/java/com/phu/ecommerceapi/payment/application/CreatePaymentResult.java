package com.phu.ecommerceapi.payment.application;

public record CreatePaymentResult(
        int httpStatus,
        String responseBody
) {

    public CreatePaymentResult {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status must be valid");
        }
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("payment response body is required");
        }
    }
}

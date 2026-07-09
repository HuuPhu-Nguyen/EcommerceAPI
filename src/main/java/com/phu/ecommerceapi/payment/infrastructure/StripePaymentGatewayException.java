package com.phu.ecommerceapi.payment.infrastructure;

class StripePaymentGatewayException extends RuntimeException {

    private final String failureCode;

    StripePaymentGatewayException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Stripe failure code is required");
        }
        this.failureCode = failureCode.trim();
    }

    String failureCode() {
        return failureCode;
    }
}

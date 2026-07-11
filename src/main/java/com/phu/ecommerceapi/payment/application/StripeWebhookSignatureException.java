package com.phu.ecommerceapi.payment.application;

public class StripeWebhookSignatureException extends RuntimeException {

    public StripeWebhookSignatureException(String message) {
        super(message);
    }
}

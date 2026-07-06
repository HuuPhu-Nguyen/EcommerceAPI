package com.phu.ecommerceapi.payment.application;

public class PaymentProviderTimeoutException extends RuntimeException {

    public PaymentProviderTimeoutException(String message) {
        super(message);
    }
}

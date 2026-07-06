package com.phu.ecommerceapi.payment.application;

public interface PaymentProvider {

    PaymentProviderResult createPayment(PaymentProviderRequest request);
}

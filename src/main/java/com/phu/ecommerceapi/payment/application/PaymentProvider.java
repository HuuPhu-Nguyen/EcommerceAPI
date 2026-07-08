package com.phu.ecommerceapi.payment.application;

public interface PaymentProvider {

    String providerCode();

    PaymentProviderCapabilities capabilities();

    PaymentProviderResult createPayment(PaymentProviderRequest request);

    PaymentRefundProviderResult refundPayment(PaymentRefundProviderRequest request);
}

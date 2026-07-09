package com.phu.ecommerceapi.payment.infrastructure;

public interface StripePaymentGateway {

    StripePaymentIntentResult createPaymentIntent(StripePaymentIntentCreateRequest request);
}

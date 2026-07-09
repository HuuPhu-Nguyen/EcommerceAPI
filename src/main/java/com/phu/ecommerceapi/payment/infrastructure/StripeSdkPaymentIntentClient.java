package com.phu.ecommerceapi.payment.infrastructure;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

final class StripeSdkPaymentIntentClient implements StripePaymentIntentClient {

    private final StripeClient stripeClient;

    StripeSdkPaymentIntentClient(StripeClient stripeClient) {
        this.stripeClient = stripeClient;
    }

    @Override
    public PaymentIntent create(PaymentIntentCreateParams params, RequestOptions options) throws StripeException {
        return stripeClient.v1().paymentIntents().create(params, options);
    }
}

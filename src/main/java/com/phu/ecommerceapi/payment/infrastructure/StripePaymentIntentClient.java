package com.phu.ecommerceapi.payment.infrastructure;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

interface StripePaymentIntentClient {

    PaymentIntent create(PaymentIntentCreateParams params, RequestOptions options) throws StripeException;
}

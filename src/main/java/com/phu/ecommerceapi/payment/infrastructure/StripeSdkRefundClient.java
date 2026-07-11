package com.phu.ecommerceapi.payment.infrastructure;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;

final class StripeSdkRefundClient implements StripeRefundClient {

    private final StripeClient stripeClient;

    StripeSdkRefundClient(StripeClient stripeClient) {
        this.stripeClient = stripeClient;
    }

    @Override
    public Refund create(RefundCreateParams params, RequestOptions options) throws StripeException {
        return stripeClient.v1().refunds().create(params, options);
    }
}

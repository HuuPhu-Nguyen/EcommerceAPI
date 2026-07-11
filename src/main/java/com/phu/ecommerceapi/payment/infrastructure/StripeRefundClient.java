package com.phu.ecommerceapi.payment.infrastructure;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;

interface StripeRefundClient {

    Refund create(RefundCreateParams params, RequestOptions options) throws StripeException;
}

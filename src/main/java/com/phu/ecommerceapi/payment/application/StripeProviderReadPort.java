package com.phu.ecommerceapi.payment.application;

import java.util.Optional;

public interface StripeProviderReadPort {

    Optional<StripePaymentIntentSnapshot> fetchPaymentIntent(String providerPaymentId);

    Optional<StripeRefundSnapshot> fetchRefund(String providerRefundId);
}

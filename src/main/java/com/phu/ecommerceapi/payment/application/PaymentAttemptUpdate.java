package com.phu.ecommerceapi.payment.application;

public record PaymentAttemptUpdate(
        PaymentAttemptView attempt,
        boolean transitioned
) {
}

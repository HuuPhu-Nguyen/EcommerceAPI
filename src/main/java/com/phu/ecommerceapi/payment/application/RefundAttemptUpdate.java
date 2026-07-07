package com.phu.ecommerceapi.payment.application;

public record RefundAttemptUpdate(
        RefundAttemptView attempt,
        boolean transitioned
) {
}

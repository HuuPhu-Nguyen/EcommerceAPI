package com.phu.ecommerceapi.payment.domain;

import java.util.Objects;

public final class PaymentStateMachine {

    private PaymentStateMachine() {
    }

    public static PaymentStatus providerSucceeded(PaymentStatus currentStatus) {
        return completePending(currentStatus, PaymentStatus.SUCCEEDED);
    }

    public static PaymentStatus providerFailed(PaymentStatus currentStatus) {
        return completePending(currentStatus, PaymentStatus.FAILED);
    }

    public static PaymentStatus providerTimedOut(PaymentStatus currentStatus) {
        return completePending(currentStatus, PaymentStatus.PROVIDER_TIMEOUT);
    }

    public static PaymentStatus refund(PaymentStatus currentStatus) {
        PaymentStatus requiredStatus = Objects.requireNonNull(currentStatus, "payment status is required");
        if (requiredStatus == PaymentStatus.REFUNDED) {
            return requiredStatus;
        }
        if (requiredStatus != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only successful payments can be refunded");
        }
        return PaymentStatus.REFUNDED;
    }

    private static PaymentStatus completePending(PaymentStatus currentStatus, PaymentStatus requestedStatus) {
        PaymentStatus requiredStatus = Objects.requireNonNull(currentStatus, "payment status is required");
        Objects.requireNonNull(requestedStatus, "requested payment status is required");
        if (requiredStatus.isTerminal()) {
            return requiredStatus;
        }
        return requestedStatus;
    }
}

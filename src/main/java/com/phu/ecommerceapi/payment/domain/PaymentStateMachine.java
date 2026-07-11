package com.phu.ecommerceapi.payment.domain;

import java.util.Objects;

public final class PaymentStateMachine {

    private PaymentStateMachine() {
    }

    public static PaymentStatus providerSucceeded(PaymentStatus currentStatus) {
        PaymentStatus requiredStatus = Objects.requireNonNull(currentStatus, "payment status is required");
        if (requiredStatus == PaymentStatus.PENDING
                || requiredStatus == PaymentStatus.FAILED
                || requiredStatus == PaymentStatus.PROVIDER_TIMEOUT) {
            return PaymentStatus.SUCCEEDED;
        }
        return requiredStatus;
    }

    public static PaymentStatus providerFailed(PaymentStatus currentStatus) {
        PaymentStatus requiredStatus = Objects.requireNonNull(currentStatus, "payment status is required");
        if (requiredStatus == PaymentStatus.PENDING || requiredStatus == PaymentStatus.PROVIDER_TIMEOUT) {
            return PaymentStatus.FAILED;
        }
        return requiredStatus;
    }

    public static PaymentStatus providerTimedOut(PaymentStatus currentStatus) {
        PaymentStatus requiredStatus = Objects.requireNonNull(currentStatus, "payment status is required");
        if (requiredStatus == PaymentStatus.PENDING) {
            return PaymentStatus.PROVIDER_TIMEOUT;
        }
        return requiredStatus;
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

}

package com.phu.ecommerceapi.payment.domain;

import java.util.Objects;

public final class RefundStateMachine {

    private RefundStateMachine() {
    }

    public static RefundStatus providerSucceeded(RefundStatus currentStatus) {
        RefundStatus requiredStatus = Objects.requireNonNull(currentStatus, "refund status is required");
        if (requiredStatus == RefundStatus.PENDING || requiredStatus == RefundStatus.PROVIDER_TIMEOUT) {
            return RefundStatus.SUCCEEDED;
        }
        return requiredStatus;
    }

    public static RefundStatus providerFailed(RefundStatus currentStatus) {
        RefundStatus requiredStatus = Objects.requireNonNull(currentStatus, "refund status is required");
        if (requiredStatus == RefundStatus.PENDING || requiredStatus == RefundStatus.PROVIDER_TIMEOUT) {
            return RefundStatus.FAILED;
        }
        return requiredStatus;
    }

    public static RefundStatus providerTimedOut(RefundStatus currentStatus) {
        RefundStatus requiredStatus = Objects.requireNonNull(currentStatus, "refund status is required");
        if (requiredStatus == RefundStatus.PENDING) {
            return RefundStatus.PROVIDER_TIMEOUT;
        }
        return requiredStatus;
    }
}

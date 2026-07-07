package com.phu.ecommerceapi.payment.domain;

import java.util.Objects;

public final class RefundStateMachine {

    private RefundStateMachine() {
    }

    public static RefundStatus providerSucceeded(RefundStatus currentStatus) {
        return completePending(currentStatus, RefundStatus.SUCCEEDED);
    }

    public static RefundStatus providerFailed(RefundStatus currentStatus) {
        return completePending(currentStatus, RefundStatus.FAILED);
    }

    public static RefundStatus providerTimedOut(RefundStatus currentStatus) {
        return completePending(currentStatus, RefundStatus.PROVIDER_TIMEOUT);
    }

    private static RefundStatus completePending(RefundStatus currentStatus, RefundStatus requestedStatus) {
        RefundStatus requiredStatus = Objects.requireNonNull(currentStatus, "refund status is required");
        Objects.requireNonNull(requestedStatus, "requested refund status is required");
        if (requiredStatus.isTerminal()) {
            return requiredStatus;
        }
        return requestedStatus;
    }
}

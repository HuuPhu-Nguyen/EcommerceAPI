package com.phu.ecommerceapi.payment.domain;

public enum RefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    PROVIDER_TIMEOUT;

    public boolean isTerminal() {
        return this != PENDING;
    }
}

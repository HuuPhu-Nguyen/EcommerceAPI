package com.phu.ecommerceapi.payment.domain;

public enum RefundStatus {
    PENDING,
    PROVIDER_SUCCEEDED_LEDGER_PENDING,
    SUCCEEDED,
    FAILED,
    PROVIDER_TIMEOUT;

    public boolean isTerminal() {
        return this != PENDING;
    }
}

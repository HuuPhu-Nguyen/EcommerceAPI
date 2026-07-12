package com.phu.ecommerceapi.payment.domain;

public enum PaymentStatus {
    PENDING,
    PROVIDER_SUCCEEDED_LEDGER_PENDING,
    SUCCEEDED,
    FAILED,
    PROVIDER_TIMEOUT,
    REFUNDED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}

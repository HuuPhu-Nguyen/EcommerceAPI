package com.phu.ecommerceapi.outbox.infrastructure;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}

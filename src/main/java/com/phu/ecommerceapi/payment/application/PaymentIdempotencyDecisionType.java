package com.phu.ecommerceapi.payment.application;

public enum PaymentIdempotencyDecisionType {
    STARTED,
    IN_PROGRESS,
    REPLAY
}

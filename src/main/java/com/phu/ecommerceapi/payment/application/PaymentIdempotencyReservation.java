package com.phu.ecommerceapi.payment.application;

public record PaymentIdempotencyReservation(
        PaymentIdempotencyEntry entry,
        boolean started
) {
}

package com.phu.ecommerceapi.payment.application;

import java.util.UUID;

public record PaymentIdempotencyRecoveryEntry(
        long recordId,
        String resourceType,
        UUID resourceId,
        String providerCode,
        String providerIdempotencyKey
) {
}

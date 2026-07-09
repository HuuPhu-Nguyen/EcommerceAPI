package com.phu.ecommerceapi.payment.application;

import java.util.UUID;

public record PaymentIdempotencyEntry(
        long recordId,
        String requestHash,
        boolean completed,
        Integer responseStatus,
        String responseBody,
        String resourceType,
        UUID resourceId,
        String providerCode,
        String providerIdempotencyKey
) {

    public boolean hasRequestHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }
}

package com.phu.ecommerceapi.payment.application;

public record PaymentIdempotencyEntry(
        long recordId,
        String requestHash,
        boolean completed,
        Integer responseStatus,
        String responseBody
) {

    public boolean hasRequestHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }
}

package com.phu.ecommerceapi.payment.application;

import java.util.UUID;

public record PaymentIdempotencyDecision(
        PaymentIdempotencyDecisionType type,
        Long recordId,
        Integer responseStatus,
        String responseBody,
        String resourceType,
        UUID resourceId,
        String providerCode,
        String providerIdempotencyKey
) {

    public static PaymentIdempotencyDecision started(long recordId) {
        return new PaymentIdempotencyDecision(
                PaymentIdempotencyDecisionType.STARTED,
                recordId,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static PaymentIdempotencyDecision inProgress(
            long recordId,
            String resourceType,
            UUID resourceId,
            String providerCode,
            String providerIdempotencyKey
    ) {
        return new PaymentIdempotencyDecision(
                PaymentIdempotencyDecisionType.IN_PROGRESS,
                recordId,
                null,
                null,
                resourceType,
                resourceId,
                providerCode,
                providerIdempotencyKey
        );
    }

    public static PaymentIdempotencyDecision replay(long recordId, int responseStatus, String responseBody) {
        return new PaymentIdempotencyDecision(
                PaymentIdempotencyDecisionType.REPLAY,
                recordId,
                responseStatus,
                responseBody,
                null,
                null,
                null,
                null
        );
    }

    public boolean shouldProcess() {
        return type == PaymentIdempotencyDecisionType.STARTED;
    }
}

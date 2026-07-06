package com.phu.ecommerceapi.payment.application;

public record PaymentIdempotencyDecision(
        PaymentIdempotencyDecisionType type,
        Long recordId,
        Integer responseStatus,
        String responseBody
) {

    public static PaymentIdempotencyDecision started(long recordId) {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.STARTED, recordId, null, null);
    }

    public static PaymentIdempotencyDecision inProgress(long recordId) {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.IN_PROGRESS, recordId, null, null);
    }

    public static PaymentIdempotencyDecision replay(long recordId, int responseStatus, String responseBody) {
        return new PaymentIdempotencyDecision(
                PaymentIdempotencyDecisionType.REPLAY,
                recordId,
                responseStatus,
                responseBody
        );
    }

    public boolean shouldProcess() {
        return type == PaymentIdempotencyDecisionType.STARTED;
    }
}

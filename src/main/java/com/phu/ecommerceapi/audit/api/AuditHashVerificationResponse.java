package com.phu.ecommerceapi.audit.api;

public record AuditHashVerificationResponse(
        boolean verified,
        long checkedEvents,
        Long brokenEventId,
        String latestHash,
        String message
) {
}

package com.phu.ecommerceapi.audit.application;

import java.time.Instant;

public record AuditHashPayload(
        String previousHash,
        String actorSubject,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String requestId,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {
}

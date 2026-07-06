package com.phu.ecommerceapi.audit.api;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
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

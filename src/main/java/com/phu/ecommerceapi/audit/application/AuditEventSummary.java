package com.phu.ecommerceapi.audit.application;

import java.time.Instant;

public record AuditEventSummary(
        Long id,
        String actorSubject,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String requestId,
        String externalCorrelationId,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        String previousHash,
        String eventHash
) {
}

package com.phu.ecommerceapi.audit.application;

import java.time.Instant;

public record AuditEventView(
        long id,
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
        String eventHash,
        String eventSignature
) {

    public AuditHashPayload toHashPayload(String previousHash) {
        return new AuditHashPayload(
                previousHash,
                actorSubject,
                action,
                resourceType,
                resourceId,
                details,
                requestId,
                ipAddress,
                userAgent,
                createdAt
        );
    }
}

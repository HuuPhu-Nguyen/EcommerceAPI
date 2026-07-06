package com.phu.ecommerceapi.audit.application;

public record AuditEventCommand(
        String actorSubject,
        String action,
        String resourceType,
        String resourceId,
        String details
) {
}

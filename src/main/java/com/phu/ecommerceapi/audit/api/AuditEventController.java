package com.phu.ecommerceapi.audit.api;

import com.phu.ecommerceapi.audit.application.AuditEventQueryService;
import com.phu.ecommerceapi.audit.application.AuditEventSummary;
import com.phu.ecommerceapi.audit.application.AuditHashVerificationResult;
import com.phu.ecommerceapi.audit.application.AuditHashVerificationService;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit/events")
public class AuditEventController {

    private final AuditEventQueryService auditEventQueryService;
    private final AuditHashVerificationService auditHashVerificationService;

    public AuditEventController(
            AuditEventQueryService auditEventQueryService,
            AuditHashVerificationService auditHashVerificationService
    ) {
        this.auditEventQueryService = auditEventQueryService;
        this.auditHashVerificationService = auditHashVerificationService;
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_AUDIT_READ)
    public List<AuditEventResponse> recentEvents(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return auditEventQueryService.recentEvents(limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/verification")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_AUDIT_READ)
    public AuditHashVerificationResponse verifyHashChain() {
        AuditHashVerificationResult result = auditHashVerificationService.verify();
        return new AuditHashVerificationResponse(
                result.verified(),
                result.checkedEvents(),
                result.brokenEventId(),
                result.latestHash(),
                result.message()
        );
    }

    private AuditEventResponse toResponse(AuditEventSummary event) {
        return new AuditEventResponse(
                event.id(),
                event.actorSubject(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.details(),
                event.requestId(),
                event.ipAddress(),
                event.userAgent(),
                event.createdAt(),
                event.previousHash(),
                event.eventHash()
        );
    }
}

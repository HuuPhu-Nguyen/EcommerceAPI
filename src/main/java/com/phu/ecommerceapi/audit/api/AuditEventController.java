package com.phu.ecommerceapi.audit.api;

import com.phu.ecommerceapi.audit.application.AuditEventQueryService;
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

    public AuditEventController(AuditEventQueryService auditEventQueryService) {
        this.auditEventQueryService = auditEventQueryService;
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_AUDIT_READ)
    public List<AuditEventResponse> recentEvents(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return auditEventQueryService.recentEvents(limit);
    }
}

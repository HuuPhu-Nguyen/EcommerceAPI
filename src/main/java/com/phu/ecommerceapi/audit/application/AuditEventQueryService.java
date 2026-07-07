package com.phu.ecommerceapi.audit.application;

import com.phu.ecommerceapi.audit.api.AuditEventResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AuditEventQueryService {

    private static final int MAX_LIMIT = 200;
    private static final Pattern SENSITIVE_DETAIL_PAIR = Pattern.compile(
            "(?i)(password|token|secret|email)=([^;\\s]+)"
    );
    private static final Pattern SENSITIVE_JSON_PAIR = Pattern.compile(
            "(?i)\"(password|token|secret|email)\"\\s*:\\s*\"[^\"]*\""
    );

    private final AuditEventPersistencePort auditEventPersistencePort;

    public AuditEventQueryService(AuditEventPersistencePort auditEventPersistencePort) {
        this.auditEventPersistencePort = auditEventPersistencePort;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> recentEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return auditEventPersistencePort.findRecentEvents(safeLimit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEventResponse toResponse(AuditEventView event) {
        return new AuditEventResponse(
                event.id(),
                maskActorSubject(event.actorSubject()),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                maskDetails(event.details()),
                event.requestId(),
                event.ipAddress(),
                event.userAgent(),
                event.createdAt(),
                event.previousHash(),
                event.eventHash()
        );
    }

    private String maskActorSubject(String actorSubject) {
        if (actorSubject == null || actorSubject.isBlank()) {
            return actorSubject;
        }
        int atIndex = actorSubject.indexOf('@');
        if (atIndex <= 0) {
            return actorSubject;
        }
        String localPart = actorSubject.substring(0, atIndex);
        String domain = actorSubject.substring(atIndex);
        return localPart.charAt(0) + "***" + domain;
    }

    private String maskDetails(String details) {
        if (details == null || details.isBlank()) {
            return details;
        }
        String masked = SENSITIVE_DETAIL_PAIR.matcher(details).replaceAll("$1=***");
        return SENSITIVE_JSON_PAIR.matcher(masked).replaceAll("\"$1\":\"***\"");
    }
}

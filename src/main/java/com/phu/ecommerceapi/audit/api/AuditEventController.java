package com.phu.ecommerceapi.audit.api;

import com.phu.ecommerceapi.audit.application.AuditEventQueryService;
import com.phu.ecommerceapi.audit.application.AuditEventSummary;
import com.phu.ecommerceapi.audit.application.AuditHashVerificationResult;
import com.phu.ecommerceapi.audit.application.AuditHashVerificationService;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.shared.api.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit/events")
@Tag(name = "Audit", description = "Tamper-evident audit event read and hash-chain verification endpoints.")
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
    @Operation(summary = "List recent audit events", description = "Returns masked audit events for admin/auditor review.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recent audit events.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = AuditEventResponse.class)),
                            examples = @ExampleObject(value = OpenApiExamples.AUDIT_EVENTS_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid bearer token.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.UNAUTHORIZED_PROBLEM)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin or auditor role with audit read scope is required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.FORBIDDEN_PROBLEM)
                    )
            )
    })
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
    @Operation(summary = "Verify the audit hash chain", description = "Checks whether persisted audit events still match their tamper-evident hash chain.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit hash-chain verification result.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuditHashVerificationResponse.class),
                            examples = @ExampleObject(value = OpenApiExamples.AUDIT_VERIFICATION_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid bearer token.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.UNAUTHORIZED_PROBLEM)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin or auditor role with audit read scope is required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.FORBIDDEN_PROBLEM)
                    )
            )
    })
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
                event.externalCorrelationId(),
                event.ipAddress(),
                event.userAgent(),
                event.createdAt(),
                event.previousHash(),
                event.eventHash(),
                event.eventSignature()
        );
    }
}

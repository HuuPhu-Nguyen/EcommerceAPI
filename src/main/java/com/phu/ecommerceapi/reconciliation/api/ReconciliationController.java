package com.phu.ecommerceapi.reconciliation.api;

import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationReport;
import com.phu.ecommerceapi.reconciliation.application.ReconciliationService;
import com.phu.ecommerceapi.shared.api.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reconciliation")
@Tag(name = "Reconciliation", description = "Money movement consistency checks across payments, refunds, and ledger records.")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/report")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_RECONCILIATION_READ)
    @Operation(
            summary = "Read latest reconciliation report",
            description = "Returns the latest completed materialized reconciliation run."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current reconciliation report.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReconciliationReport.class),
                            examples = @ExampleObject(value = OpenApiExamples.RECONCILIATION_REPORT_RESPONSE)
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
    public ReconciliationReport report() {
        return reconciliationService.latestCompletedReport();
    }

    @PostMapping("/runs")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_RECONCILIATION_READ)
    @Operation(
            summary = "Start reconciliation run",
            description = "Runs bounded reconciliation synchronously for local and demo use, then stores and returns the report."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Stored reconciliation report for the completed run.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReconciliationReport.class),
                            examples = @ExampleObject(value = OpenApiExamples.RECONCILIATION_REPORT_RESPONSE)
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
    public ReconciliationReport startRun() {
        return reconciliationService.runReport();
    }
}

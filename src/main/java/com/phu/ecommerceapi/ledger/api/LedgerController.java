package com.phu.ecommerceapi.ledger.api;

import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.ledger.application.LedgerQueryService;
import com.phu.ecommerceapi.ledger.application.LedgerTransactionView;
import com.phu.ecommerceapi.shared.api.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/ledger")
@Tag(name = "Ledger", description = "Read-only immutable ledger views for admin and auditor review.")
public class LedgerController {

    private final LedgerQueryService ledgerQueryService;

    public LedgerController(LedgerQueryService ledgerQueryService) {
        this.ledgerQueryService = ledgerQueryService;
    }

    @GetMapping("/transactions")
    @PreAuthorize(SecurityExpressions.ADMIN_OR_AUDITOR_LEDGER_READ)
    @Operation(
            summary = "List recent ledger transactions",
            description = "Returns immutable ledger transactions with debit/credit entries for audit review."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recent ledger transactions.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = LedgerTransactionView.class)),
                            examples = @ExampleObject(value = OpenApiExamples.LEDGER_TRANSACTIONS_RESPONSE)
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
                    description = "Admin or auditor role with ledger read scope is required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.FORBIDDEN_PROBLEM)
                    )
            )
    })
    public List<LedgerTransactionView> recentTransactions(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ledgerQueryService.recentTransactions(limit);
    }
}

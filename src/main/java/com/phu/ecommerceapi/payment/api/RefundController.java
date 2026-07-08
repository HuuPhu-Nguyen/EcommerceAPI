package com.phu.ecommerceapi.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.payment.application.CreateRefundCommand;
import com.phu.ecommerceapi.payment.application.CreateRefundResult;
import com.phu.ecommerceapi.payment.application.CreateRefundUseCase;
import com.phu.ecommerceapi.payment.application.RefundAttemptResponse;
import com.phu.ecommerceapi.shared.api.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments/{paymentId}/refunds")
@Tag(name = "Refunds", description = "Idempotent refund workflow with reversing ledger entries.")
public class RefundController {

    private final CreateRefundUseCase createRefundUseCase;
    private final ObjectMapper objectMapper;

    public RefundController(CreateRefundUseCase createRefundUseCase, ObjectMapper objectMapper) {
        this.createRefundUseCase = createRefundUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_PAYMENT_REFUND)
    @Operation(
            summary = "Refund a payment",
            description = "Creates an idempotent refund for a succeeded payment and posts reversing ledger entries when approved."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CreateRefundRequest.class),
                    examples = @ExampleObject(value = OpenApiExamples.CREATE_REFUND_REQUEST)
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Refund attempt completed or idempotently replayed.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefundAttemptResponse.class),
                            examples = @ExampleObject(value = OpenApiExamples.REFUND_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid refund request.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.VALIDATION_PROBLEM)
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
                    description = "Customer role, refund scope, and ownership are required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.FORBIDDEN_PROBLEM)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency conflict or payment is not refundable.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.CONFLICT_PROBLEM)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Original payment provider is unavailable for refund routing.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.SERVICE_UNAVAILABLE_PROBLEM)
                    )
            )
    })
    public ResponseEntity<String> createRefund(
            @Parameter(description = "Payment identifier returned by the payment endpoint.")
            @PathVariable UUID paymentId,
            @Parameter(
                    description = "Required idempotency key scoped by customer, endpoint, and operation.",
                    example = "refund-demo-key-001",
                    required = true
            )
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String requestBody,
            @Parameter(hidden = true)
            @AuthenticatedUser CurrentUser currentUser
    ) {
        CreateRefundRequest request = parseRequest(requestBody);
        CreateRefundResult result = createRefundUseCase.refund(new CreateRefundCommand(
                paymentId,
                currentUser,
                idempotencyKey,
                requestBody,
                request.reason()
        ));
        return ResponseEntity
                .status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.responseBody());
    }

    private CreateRefundRequest parseRequest(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("refund request body is required");
        }
        try {
            return objectMapper.readValue(requestBody, CreateRefundRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("refund request body is invalid", exception);
        }
    }
}

package com.phu.ecommerceapi.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.payment.application.CreatePaymentCommand;
import com.phu.ecommerceapi.payment.application.CreatePaymentResult;
import com.phu.ecommerceapi.payment.application.CreatePaymentUseCase;
import com.phu.ecommerceapi.payment.application.PaymentAttemptResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Idempotent provider-selected payment creation with stable replay semantics.")
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final ObjectMapper objectMapper;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase, ObjectMapper objectMapper) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_PAYMENT_CREATE)
    @Operation(
            summary = "Create a payment",
            description = "Creates a payment attempt for a payable order. Reusing the same Idempotency-Key and body returns the original response; reusing the key with a different body returns 409."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CreatePaymentRequest.class),
                    examples = @ExampleObject(value = OpenApiExamples.CREATE_PAYMENT_REQUEST)
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment attempt completed or idempotently replayed.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentAttemptResponse.class),
                            examples = @ExampleObject(value = OpenApiExamples.PAYMENT_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payment request.",
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
                    description = "Customer role and payment create scope are required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.FORBIDDEN_PROBLEM)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency conflict or payment state conflict.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.CONFLICT_PROBLEM)
                    )
            )
    })
    public ResponseEntity<String> createPayment(
            @Parameter(
                    description = "Required idempotency key scoped by customer, endpoint, and operation.",
                    example = "payment-demo-key-001",
                    required = true
            )
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String requestBody,
            @Parameter(hidden = true)
            @AuthenticatedUser CurrentUser currentUser
    ) {
        CreatePaymentRequest request = parseRequest(requestBody);
        CreatePaymentResult result = createPaymentUseCase.create(new CreatePaymentCommand(
                currentUser,
                idempotencyKey,
                requestBody,
                request.orderId(),
                request.provider(),
                request.paymentMethodToken()
        ));
        return ResponseEntity
                .status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.responseBody());
    }

    private CreatePaymentRequest parseRequest(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("payment request body is required");
        }
        try {
            return objectMapper.readValue(requestBody, CreatePaymentRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("payment request body is invalid", exception);
        }
    }
}

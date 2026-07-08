package com.phu.ecommerceapi.checkout.api;

import com.phu.ecommerceapi.checkout.application.CheckoutResponse;
import com.phu.ecommerceapi.checkout.application.CheckoutService;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.shared.api.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
@Tag(name = "Checkout", description = "Cart checkout with atomic inventory reservation and pending order creation.")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_CHECKOUT_CREATE)
    @Operation(
            summary = "Checkout a cart",
            description = "Reserves inventory atomically, creates a PENDING_PAYMENT order, and writes audit/outbox records."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CheckoutRequest.class),
                    examples = @ExampleObject(value = OpenApiExamples.CHECKOUT_REQUEST)
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order created and waiting for payment.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CheckoutResponse.class),
                            examples = @ExampleObject(value = OpenApiExamples.CHECKOUT_RESPONSE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid checkout request.",
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
                    description = "Customer role and checkout scope are required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.FORBIDDEN_PROBLEM)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Inventory cannot be reserved or cart/order state conflicts.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = OpenApiExamples.CONFLICT_PROBLEM)
                    )
            )
    })
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @Parameter(hidden = true)
            @AuthenticatedUser CurrentUser currentUser
    ) {
        return ResponseEntity.ok(checkoutService.checkout(request.cartId(), currentUser));
    }
}

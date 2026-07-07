package com.phu.ecommerceapi.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.payment.application.CreateRefundCommand;
import com.phu.ecommerceapi.payment.application.CreateRefundResult;
import com.phu.ecommerceapi.payment.application.CreateRefundUseCase;
import org.springframework.http.MediaType;
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
public class RefundController {

    private final CreateRefundUseCase createRefundUseCase;
    private final ObjectMapper objectMapper;

    public RefundController(CreateRefundUseCase createRefundUseCase, ObjectMapper objectMapper) {
        this.createRefundUseCase = createRefundUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_PAYMENT_REFUND)
    public ResponseEntity<String> createRefund(
            @PathVariable UUID paymentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String requestBody,
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

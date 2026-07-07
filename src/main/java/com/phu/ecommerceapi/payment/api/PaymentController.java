package com.phu.ecommerceapi.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
import com.phu.ecommerceapi.payment.application.CreatePaymentCommand;
import com.phu.ecommerceapi.payment.application.CreatePaymentResult;
import com.phu.ecommerceapi.payment.application.CreatePaymentUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final ObjectMapper objectMapper;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase, ObjectMapper objectMapper) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_PAYMENT_CREATE)
    public ResponseEntity<String> createPayment(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String requestBody,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        CreatePaymentRequest request = parseRequest(requestBody);
        CreatePaymentResult result = createPaymentUseCase.create(new CreatePaymentCommand(
                currentUser,
                idempotencyKey,
                requestBody,
                request.orderId(),
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

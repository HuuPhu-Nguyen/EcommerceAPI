package com.phu.ecommerceapi.payment.api;

import com.phu.ecommerceapi.identity.api.AuthenticatedUser;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.SecurityExpressions;
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

    public RefundController(CreateRefundUseCase createRefundUseCase) {
        this.createRefundUseCase = createRefundUseCase;
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.CUSTOMER_PAYMENT_REFUND)
    public ResponseEntity<String> createRefund(
            @PathVariable UUID paymentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String requestBody,
            @AuthenticatedUser CurrentUser currentUser
    ) {
        CreateRefundResult result = createRefundUseCase.refund(
                paymentId,
                currentUser,
                idempotencyKey,
                requestBody
        );
        return ResponseEntity
                .status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.responseBody());
    }
}

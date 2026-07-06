package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.payment.api.CreateRefundRequest;
import com.phu.ecommerceapi.payment.api.RefundResponse;
import com.phu.ecommerceapi.payment.infrastructure.FakePaymentProvider;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreateRefundUseCase {

    private static final String OPERATION = "REFUND_PAYMENT";
    private static final String PROVIDER_OUTCOME_FAILED_REASON = "fake_provider_declined";
    private static final String PROVIDER_OUTCOME_TIMEOUT_REASON = "fake_provider_timeout";

    private final ObjectMapper objectMapper;
    private final UserRepo userRepo;
    private final PaymentIdempotencyService idempotencyService;
    private final RefundAttemptService refundAttemptService;
    private final PaymentProvider paymentProvider;

    public CreateRefundUseCase(
            ObjectMapper objectMapper,
            UserRepo userRepo,
            PaymentIdempotencyService idempotencyService,
            RefundAttemptService refundAttemptService,
            PaymentProvider paymentProvider
    ) {
        this.objectMapper = objectMapper;
        this.userRepo = userRepo;
        this.idempotencyService = idempotencyService;
        this.refundAttemptService = refundAttemptService;
        this.paymentProvider = paymentProvider;
    }

    public CreateRefundResult refund(
            UUID paymentId,
            CurrentUser currentUser,
            String idempotencyKey,
            String requestBody
    ) {
        CreateRefundRequest request = parseRequest(requestBody);
        UserModel customer = resolveCustomer(currentUser);

        PaymentIdempotencyCommand idempotencyCommand = new PaymentIdempotencyCommand(
                customer.getId(),
                endpoint(paymentId),
                OPERATION,
                idempotencyKey,
                requestBody
        );

        Optional<PaymentIdempotencyDecision> existingDecision = idempotencyService.findExisting(idempotencyCommand);
        if (existingDecision.isPresent()) {
            return replayOrReject(existingDecision.get());
        }

        refundAttemptService.validateRefundable(customer.getId(), paymentId);
        PaymentIdempotencyDecision idempotencyDecision = idempotencyService.start(idempotencyCommand);
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return replayOrReject(idempotencyDecision);
        }
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            return replayOrReject(idempotencyDecision);
        }

        RefundAttemptSnapshot attempt = refundAttemptService.startAttempt(
                customer.getId(),
                paymentId,
                idempotencyKey,
                request.reason()
        );

        RefundResponse response;
        int httpStatus = HttpStatus.OK.value();
        try {
            PaymentRefundProviderResult providerResult = paymentProvider.refundPayment(new PaymentRefundProviderRequest(
                    attempt.paymentId(),
                    attempt.providerPaymentId(),
                    attempt.amount(),
                    attempt.currency(),
                    providerIdempotencyKey(customer.getId(), attempt, idempotencyKey),
                    providerMetadata(request)
            ));
            response = refundAttemptService.completeAttempt(attempt.refundId(), providerResult, currentUser);
        } catch (PaymentProviderTimeoutException exception) {
            response = refundAttemptService.markProviderTimeout(
                    attempt.refundId(),
                    exception.getMessage(),
                    currentUser
            );
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        }

        String responseBody = serialize(response);
        idempotencyService.complete(idempotencyDecision.recordId(), httpStatus, responseBody);
        return new CreateRefundResult(httpStatus, responseBody);
    }

    private CreateRefundResult replayOrReject(PaymentIdempotencyDecision decision) {
        if (decision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return new CreateRefundResult(decision.responseStatus(), decision.responseBody());
        }
        if (decision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            throw new ConflictException("Refund request is already in progress");
        }
        throw new IllegalStateException("Unexpected idempotency decision: " + decision.type());
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

    private UserModel resolveCustomer(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authenticated customer is required");
        }

        UserModel customer = userRepo.findByUsername(currentUser.username());
        if (customer == null && currentUser.email() != null) {
            customer = userRepo.findByEmail(currentUser.email());
        }
        if (customer == null) {
            throw new NotFoundException("Customer profile not found");
        }
        return customer;
    }

    private String endpoint(UUID paymentId) {
        return "/payments/%s/refunds".formatted(paymentId);
    }

    private String providerIdempotencyKey(long customerId, RefundAttemptSnapshot attempt, String idempotencyKey) {
        return "refund:%d:%s:%s".formatted(customerId, attempt.paymentId(), idempotencyKey.trim());
    }

    private Map<String, String> providerMetadata(CreateRefundRequest request) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("refundReason", request.reason());
        if (PROVIDER_OUTCOME_FAILED_REASON.equalsIgnoreCase(request.reason())) {
            metadata.put(FakePaymentProvider.OUTCOME_METADATA_KEY, FakePaymentProvider.OUTCOME_FAILURE);
        } else if (PROVIDER_OUTCOME_TIMEOUT_REASON.equalsIgnoreCase(request.reason())) {
            metadata.put(FakePaymentProvider.OUTCOME_METADATA_KEY, FakePaymentProvider.OUTCOME_TIMEOUT);
        }
        return metadata;
    }

    private String serialize(RefundResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Refund response could not be serialized", exception);
        }
    }
}

package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfileLookup;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import com.phu.ecommerceapi.shared.api.ServiceUnavailableException;
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
    private static final String REFUND_RESOURCE_TYPE = "REFUND";
    private static final String PENDING_STATUS = "PENDING";
    private static final String PROVIDER_TIMEOUT_STATUS = "PROVIDER_TIMEOUT";
    private static final String PROVIDER_OUTCOME_FAILED_REASON = "fake_provider_declined";
    private static final String PROVIDER_OUTCOME_TIMEOUT_REASON = "fake_provider_timeout";

    private final ObjectMapper objectMapper;
    private final CustomerProfileLookup customerProfileLookup;
    private final PaymentIdempotencyService idempotencyService;
    private final RefundAttemptService refundAttemptService;
    private final PaymentProviderRegistry paymentProviderRegistry;

    public CreateRefundUseCase(
            ObjectMapper objectMapper,
            CustomerProfileLookup customerProfileLookup,
            PaymentIdempotencyService idempotencyService,
            RefundAttemptService refundAttemptService,
            PaymentProviderRegistry paymentProviderRegistry
    ) {
        this.objectMapper = objectMapper;
        this.customerProfileLookup = customerProfileLookup;
        this.idempotencyService = idempotencyService;
        this.refundAttemptService = refundAttemptService;
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    public CreateRefundResult refund(CreateRefundCommand command) {
        long customerId = resolveCustomerId(command.currentUser());

        PaymentIdempotencyCommand idempotencyCommand = new PaymentIdempotencyCommand(
                customerId,
                endpoint(command.paymentId()),
                OPERATION,
                command.idempotencyKey(),
                command.requestBody()
        );

        Optional<PaymentIdempotencyDecision> existingDecision = idempotencyService.findExisting(idempotencyCommand);
        if (existingDecision.isPresent()) {
            return replayOrRecover(existingDecision.get());
        }

        RefundablePayment refundablePayment = refundAttemptService.validateRefundable(customerId, command.paymentId());
        PaymentProvider refundProvider = resolveRefundProvider(refundablePayment, command.currentUser());
        PaymentIdempotencyDecision idempotencyDecision = idempotencyService.start(idempotencyCommand);
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return replayOrRecover(idempotencyDecision);
        }
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            return replayOrRecover(idempotencyDecision);
        }

        RefundAttemptSnapshot attempt = refundAttemptService.startAttempt(
                customerId,
                command.paymentId(),
                command.idempotencyKey(),
                command.reason()
        );
        idempotencyService.linkRefundAttempt(
                idempotencyDecision.recordId(),
                attempt.refundId(),
                attempt.providerCode(),
                attempt.providerIdempotencyKey()
        );

        RefundAttemptResponse response;
        int httpStatus = HttpStatus.OK.value();
        try {
            PaymentRefundProviderResult providerResult = refundProvider.refundPayment(new PaymentRefundProviderRequest(
                    attempt.refundId(),
                    attempt.paymentId(),
                    attempt.providerPaymentId(),
                    attempt.amount(),
                    attempt.currency(),
                    attempt.providerIdempotencyKey(),
                    providerMetadata(command)
            ));
            response = refundAttemptService.completeAttempt(attempt.refundId(), providerResult, command.currentUser());
        } catch (PaymentProviderTimeoutException exception) {
            response = refundAttemptService.markProviderTimeout(
                    attempt.refundId(),
                    exception.getMessage(),
                    command.currentUser()
            );
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        }

        String responseBody = serialize(response);
        idempotencyService.complete(idempotencyDecision.recordId(), httpStatus, responseBody);
        return new CreateRefundResult(httpStatus, responseBody);
    }

    private PaymentProvider resolveRefundProvider(RefundablePayment payment, CurrentUser actor) {
        try {
            return paymentProviderRegistry.resolveExistingProvider(payment.providerCode());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            refundAttemptService.recordProviderUnavailable(
                    actor,
                    payment.paymentId(),
                    payment.providerCode(),
                    exception.getMessage()
            );
            throw new ServiceUnavailableException(
                    "Payment provider is unavailable for refund: " + payment.providerCode()
            );
        }
    }

    private CreateRefundResult replayOrRecover(PaymentIdempotencyDecision decision) {
        if (decision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return new CreateRefundResult(decision.responseStatus(), decision.responseBody());
        }
        if (decision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            if (REFUND_RESOURCE_TYPE.equals(decision.resourceType()) && decision.resourceId() != null) {
                return recoverLinkedRefundAttempt(decision);
            }
            throw new ConflictException("Refund request is already in progress");
        }
        throw new IllegalStateException("Unexpected idempotency decision: " + decision.type());
    }

    private CreateRefundResult recoverLinkedRefundAttempt(PaymentIdempotencyDecision decision) {
        RefundAttemptResponse response = refundAttemptService.findAttemptResponse(decision.resourceId())
                .orElseThrow(() -> new ConflictException("Refund request is already in progress"));

        if (PROVIDER_TIMEOUT_STATUS.equals(response.status())) {
            throw new ConflictException("Refund provider outcome is unknown; retry after reconciliation");
        }
        if (PENDING_STATUS.equals(response.status()) && response.providerRefundId() == null) {
            throw new ConflictException("Refund request is already in progress");
        }

        int httpStatus = HttpStatus.OK.value();
        String responseBody = serialize(response);
        idempotencyService.complete(decision.recordId(), httpStatus, responseBody);
        return new CreateRefundResult(httpStatus, responseBody);
    }

    private long resolveCustomerId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authenticated customer is required");
        }

        CustomerProfile customer = customerProfileLookup.findCurrentUserProfile(currentUser)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
        return customer.customerId();
    }

    private String endpoint(UUID paymentId) {
        return "/payments/%s/refunds".formatted(paymentId);
    }

    private Map<String, String> providerMetadata(CreateRefundCommand command) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("refundReason", command.reason());
        if (PROVIDER_OUTCOME_FAILED_REASON.equalsIgnoreCase(command.reason())) {
            metadata.put(
                    PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                    PaymentProviderOutcomeMetadata.OUTCOME_FAILURE
            );
        } else if (PROVIDER_OUTCOME_TIMEOUT_REASON.equalsIgnoreCase(command.reason())) {
            metadata.put(
                    PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                    PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT
            );
        }
        return metadata;
    }

    private String serialize(RefundAttemptResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Refund response could not be serialized", exception);
        }
    }
}

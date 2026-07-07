package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.customer.application.CustomerProfile;
import com.phu.ecommerceapi.customer.application.CustomerProfileLookup;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.api.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CreatePaymentUseCase {

    private static final String ENDPOINT = "/payments";
    private static final String OPERATION = "CREATE_PAYMENT";
    private static final String PROVIDER_TOKEN_DECLINED = "pm_card_declined";
    private static final String PROVIDER_TOKEN_TIMEOUT = "pm_provider_timeout";

    private final ObjectMapper objectMapper;
    private final CustomerProfileLookup customerProfileLookup;
    private final PaymentIdempotencyService idempotencyService;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentProvider paymentProvider;

    public CreatePaymentUseCase(
            ObjectMapper objectMapper,
            CustomerProfileLookup customerProfileLookup,
            PaymentIdempotencyService idempotencyService,
            PaymentAttemptService paymentAttemptService,
            PaymentProvider paymentProvider
    ) {
        this.objectMapper = objectMapper;
        this.customerProfileLookup = customerProfileLookup;
        this.idempotencyService = idempotencyService;
        this.paymentAttemptService = paymentAttemptService;
        this.paymentProvider = paymentProvider;
    }

    public CreatePaymentResult create(CreatePaymentCommand command) {
        long customerId = resolveCustomerId(command.currentUser());

        PaymentIdempotencyCommand idempotencyCommand = new PaymentIdempotencyCommand(
                customerId,
                ENDPOINT,
                OPERATION,
                command.idempotencyKey(),
                command.requestBody()
        );

        Optional<PaymentIdempotencyDecision> existingDecision = idempotencyService.findExisting(idempotencyCommand);
        if (existingDecision.isPresent()) {
            return replayOrReject(existingDecision.get());
        }

        paymentAttemptService.validatePayable(customerId, command.orderId());
        PaymentIdempotencyDecision idempotencyDecision = idempotencyService.start(idempotencyCommand);
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return replayOrReject(idempotencyDecision);
        }
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            return replayOrReject(idempotencyDecision);
        }

        PaymentAttemptSnapshot attempt = paymentAttemptService.startAttempt(
                customerId,
                command.orderId(),
                command.idempotencyKey()
        );

        PaymentAttemptResponse response;
        int httpStatus = HttpStatus.OK.value();
        try {
            PaymentProviderResult providerResult = paymentProvider.createPayment(new PaymentProviderRequest(
                    attempt.orderId(),
                    attempt.amount(),
                    attempt.currency(),
                    providerIdempotencyKey(customerId, attempt, command.idempotencyKey()),
                    providerMetadata(command)
            ));
            response = paymentAttemptService.completeAttempt(attempt.paymentId(), providerResult, command.currentUser());
        } catch (PaymentProviderTimeoutException exception) {
            response = paymentAttemptService.markProviderTimeout(
                    attempt.paymentId(),
                    exception.getMessage(),
                    command.currentUser()
            );
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        }

        String responseBody = serialize(response);
        idempotencyService.complete(idempotencyDecision.recordId(), httpStatus, responseBody);
        return new CreatePaymentResult(httpStatus, responseBody);
    }

    private CreatePaymentResult replayOrReject(PaymentIdempotencyDecision decision) {
        if (decision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return new CreatePaymentResult(decision.responseStatus(), decision.responseBody());
        }
        if (decision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            throw new ConflictException("Payment request is already in progress");
        }
        throw new IllegalStateException("Unexpected idempotency decision: " + decision.type());
    }

    private long resolveCustomerId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authenticated customer is required");
        }

        CustomerProfile customer = customerProfileLookup.findCurrentUserProfile(currentUser)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
        return customer.customerId();
    }

    private String providerIdempotencyKey(long customerId, PaymentAttemptSnapshot attempt, String idempotencyKey) {
        return "payment:%d:%s:%s".formatted(customerId, attempt.orderId(), idempotencyKey.trim());
    }

    private Map<String, String> providerMetadata(CreatePaymentCommand command) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("paymentMethodToken", command.paymentMethodToken());
        if (PROVIDER_TOKEN_DECLINED.equalsIgnoreCase(command.paymentMethodToken())) {
            metadata.put(
                    PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                    PaymentProviderOutcomeMetadata.OUTCOME_FAILURE
            );
        } else if (PROVIDER_TOKEN_TIMEOUT.equalsIgnoreCase(command.paymentMethodToken())) {
            metadata.put(
                    PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                    PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT
            );
        }
        return metadata;
    }

    private String serialize(PaymentAttemptResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payment response could not be serialized", exception);
        }
    }
}

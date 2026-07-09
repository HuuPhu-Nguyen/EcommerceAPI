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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreatePaymentUseCase {

    private static final String ENDPOINT = "/payments";
    private static final String OPERATION = "CREATE_PAYMENT";
    private static final String PAYMENT_RESOURCE_TYPE = "PAYMENT";
    private static final String PROVIDER_TOKEN_DECLINED = "pm_card_declined";
    private static final String PROVIDER_TOKEN_TIMEOUT = "pm_provider_timeout";

    private final ObjectMapper objectMapper;
    private final CustomerProfileLookup customerProfileLookup;
    private final PaymentIdempotencyService idempotencyService;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentProviderAvailabilityService paymentProviderAvailabilityService;

    public CreatePaymentUseCase(
            ObjectMapper objectMapper,
            CustomerProfileLookup customerProfileLookup,
            PaymentIdempotencyService idempotencyService,
            PaymentAttemptService paymentAttemptService,
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentProviderAvailabilityService paymentProviderAvailabilityService
    ) {
        this.objectMapper = objectMapper;
        this.customerProfileLookup = customerProfileLookup;
        this.idempotencyService = idempotencyService;
        this.paymentAttemptService = paymentAttemptService;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentProviderAvailabilityService = paymentProviderAvailabilityService;
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
            return replayOrRecover(existingDecision.get());
        }

        PaymentProvider paymentProvider = paymentProviderRegistry.resolveForPayment(command.provider());
        String providerCode = paymentProvider.providerCode();
        PaymentPayableOrder payableOrder = paymentAttemptService.validatePayable(
                customerId,
                command.orderId(),
                providerCode
        );
        assertProviderAllowed(providerCode, payableOrder);

        PaymentIdempotencyDecision idempotencyDecision = idempotencyService.start(idempotencyCommand);
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return replayOrRecover(idempotencyDecision);
        }
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            return replayOrRecover(idempotencyDecision);
        }

        String providerIdempotencyKey = providerIdempotencyKey(
                customerId,
                providerCode,
                command.orderId(),
                command.idempotencyKey()
        );
        PaymentAttemptSnapshot attempt = paymentAttemptService.startAttempt(
                customerId,
                command.orderId(),
                command.idempotencyKey(),
                providerCode,
                providerIdempotencyKey
        );
        idempotencyService.linkPaymentAttempt(
                idempotencyDecision.recordId(),
                attempt.paymentId(),
                attempt.providerCode(),
                attempt.providerIdempotencyKey()
        );

        PaymentAttemptResponse response;
        int httpStatus = HttpStatus.OK.value();
        try {
            PaymentProviderResult providerResult = paymentProvider.createPayment(new PaymentProviderRequest(
                    attempt.paymentId(),
                    attempt.orderId(),
                    attempt.amount(),
                    attempt.currency(),
                    attempt.providerIdempotencyKey(),
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

    private CreatePaymentResult replayOrRecover(PaymentIdempotencyDecision decision) {
        if (decision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return new CreatePaymentResult(decision.responseStatus(), decision.responseBody());
        }
        if (decision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            if (PAYMENT_RESOURCE_TYPE.equals(decision.resourceType()) && decision.resourceId() != null) {
                return recoverLinkedPaymentAttempt(decision);
            }
            throw new ConflictException("Payment request is already in progress");
        }
        throw new IllegalStateException("Unexpected idempotency decision: " + decision.type());
    }

    private CreatePaymentResult recoverLinkedPaymentAttempt(PaymentIdempotencyDecision decision) {
        PaymentAttemptResponse response = paymentAttemptService.findAttemptResponse(decision.resourceId())
                .orElseThrow(() -> new ConflictException("Payment request is already in progress"));

        if ("PROVIDER_TIMEOUT".equals(response.status())) {
            throw new ConflictException("Payment provider outcome is unknown; retry after reconciliation");
        }
        if ("PENDING".equals(response.status()) && response.providerPaymentId() == null) {
            throw new ConflictException("Payment request is already in progress");
        }

        int httpStatus = HttpStatus.OK.value();
        String responseBody = serialize(response);
        idempotencyService.complete(decision.recordId(), httpStatus, responseBody);
        return new CreatePaymentResult(httpStatus, responseBody);
    }

    private long resolveCustomerId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authenticated customer is required");
        }

        CustomerProfile customer = customerProfileLookup.findCurrentUserProfile(currentUser)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
        return customer.customerId();
    }

    private void assertProviderAllowed(String providerCode, PaymentPayableOrder order) {
        List<String> allowedProviders = paymentProviderAvailabilityService.allowedProviderCodes(
                order.amount(),
                order.currency()
        );
        if (!allowedProviders.contains(providerCode)) {
            throw new ConflictException("Payment provider is not available for this order");
        }
    }

    private String providerIdempotencyKey(
            long customerId,
            String providerCode,
            UUID orderId,
            String idempotencyKey
    ) {
        return "payment:%s:%d:%s:%s".formatted(
                providerCode,
                customerId,
                orderId,
                idempotencyKey.trim()
        );
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

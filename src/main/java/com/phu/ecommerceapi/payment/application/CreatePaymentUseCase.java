package com.phu.ecommerceapi.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.User.UserRepo;
import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.payment.api.CreatePaymentRequest;
import com.phu.ecommerceapi.payment.api.PaymentResponse;
import com.phu.ecommerceapi.payment.infrastructure.FakePaymentProvider;
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
    private final UserRepo userRepo;
    private final PaymentIdempotencyService idempotencyService;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentProvider paymentProvider;

    public CreatePaymentUseCase(
            ObjectMapper objectMapper,
            UserRepo userRepo,
            PaymentIdempotencyService idempotencyService,
            PaymentAttemptService paymentAttemptService,
            PaymentProvider paymentProvider
    ) {
        this.objectMapper = objectMapper;
        this.userRepo = userRepo;
        this.idempotencyService = idempotencyService;
        this.paymentAttemptService = paymentAttemptService;
        this.paymentProvider = paymentProvider;
    }

    public CreatePaymentResult create(CurrentUser currentUser, String idempotencyKey, String requestBody) {
        CreatePaymentRequest request = parseRequest(requestBody);
        UserModel customer = resolveCustomer(currentUser);

        PaymentIdempotencyCommand idempotencyCommand = new PaymentIdempotencyCommand(
                customer.getId(),
                ENDPOINT,
                OPERATION,
                idempotencyKey,
                requestBody
        );

        Optional<PaymentIdempotencyDecision> existingDecision = idempotencyService.findExisting(idempotencyCommand);
        if (existingDecision.isPresent()) {
            return replayOrReject(existingDecision.get());
        }

        paymentAttemptService.validatePayable(customer.getId(), request.orderId());
        PaymentIdempotencyDecision idempotencyDecision = idempotencyService.start(idempotencyCommand);
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.REPLAY) {
            return replayOrReject(idempotencyDecision);
        }
        if (idempotencyDecision.type() == PaymentIdempotencyDecisionType.IN_PROGRESS) {
            return replayOrReject(idempotencyDecision);
        }

        PaymentAttemptSnapshot attempt = paymentAttemptService.startAttempt(
                customer.getId(),
                request.orderId(),
                idempotencyKey
        );

        PaymentResponse response;
        int httpStatus = HttpStatus.OK.value();
        try {
            PaymentProviderResult providerResult = paymentProvider.createPayment(new PaymentProviderRequest(
                    attempt.orderId(),
                    attempt.amount(),
                    attempt.currency(),
                    providerIdempotencyKey(customer.getId(), attempt, idempotencyKey),
                    providerMetadata(request)
            ));
            response = paymentAttemptService.completeAttempt(attempt.paymentId(), providerResult, currentUser);
        } catch (PaymentProviderTimeoutException exception) {
            response = paymentAttemptService.markProviderTimeout(attempt.paymentId(), exception.getMessage(), currentUser);
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

    private String providerIdempotencyKey(long customerId, PaymentAttemptSnapshot attempt, String idempotencyKey) {
        return "payment:%d:%s:%s".formatted(customerId, attempt.orderId(), idempotencyKey.trim());
    }

    private Map<String, String> providerMetadata(CreatePaymentRequest request) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("paymentMethodToken", request.paymentMethodToken());
        if (PROVIDER_TOKEN_DECLINED.equalsIgnoreCase(request.paymentMethodToken())) {
            metadata.put(FakePaymentProvider.OUTCOME_METADATA_KEY, FakePaymentProvider.OUTCOME_FAILURE);
        } else if (PROVIDER_TOKEN_TIMEOUT.equalsIgnoreCase(request.paymentMethodToken())) {
            metadata.put(FakePaymentProvider.OUTCOME_METADATA_KEY, FakePaymentProvider.OUTCOME_TIMEOUT);
        }
        return metadata;
    }

    private String serialize(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payment response could not be serialized", exception);
        }
    }
}

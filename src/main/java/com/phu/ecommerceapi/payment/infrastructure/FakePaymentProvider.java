package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentProvider;
import com.phu.ecommerceapi.payment.application.PaymentProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "app.payment-provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentProvider implements PaymentProvider {

    public static final String OUTCOME_METADATA_KEY = "fakeOutcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
    public static final String OUTCOME_TIMEOUT = "timeout";

    private static final String DECLINED_CODE = "fake_declined";

    private final ConcurrentMap<String, PaymentProviderResult> processedRequests = new ConcurrentHashMap<>();

    @Override
    public PaymentProviderResult createPayment(PaymentProviderRequest request) {
        PaymentProviderResult existingResult = processedRequests.get(request.idempotencyKey());
        if (existingResult != null) {
            return duplicate(existingResult);
        }

        PaymentProviderResult result = switch (requestedOutcome(request)) {
            case OUTCOME_SUCCESS -> PaymentProviderResult.succeeded(
                    providerPaymentId(request),
                    "Fake payment approved"
            );
            case OUTCOME_FAILURE -> PaymentProviderResult.failed(
                    providerPaymentId(request),
                    DECLINED_CODE,
                    "Fake payment declined"
            );
            case OUTCOME_TIMEOUT -> throw new PaymentProviderTimeoutException(
                    "Fake payment provider timed out for order " + request.orderId()
            );
            default -> throw new IllegalArgumentException("Unsupported fake payment outcome");
        };

        PaymentProviderResult previousResult = processedRequests.putIfAbsent(request.idempotencyKey(), result);
        if (previousResult != null) {
            return duplicate(previousResult);
        }
        return result;
    }

    private PaymentProviderResult duplicate(PaymentProviderResult existingResult) {
        return PaymentProviderResult.duplicate(
                existingResult.providerPaymentId(),
                "Duplicate fake provider request"
        );
    }

    private String requestedOutcome(PaymentProviderRequest request) {
        String outcome = request.metadata().getOrDefault(OUTCOME_METADATA_KEY, OUTCOME_SUCCESS);
        return switch (outcome.trim().toLowerCase(Locale.ROOT)) {
            case "success", "succeeded", "approved" -> OUTCOME_SUCCESS;
            case "failure", "failed", "declined" -> OUTCOME_FAILURE;
            case "timeout" -> OUTCOME_TIMEOUT;
            default -> throw new IllegalArgumentException("Unsupported fake payment outcome: " + outcome);
        };
    }

    private String providerPaymentId(PaymentProviderRequest request) {
        UUID deterministicId = UUID.nameUUIDFromBytes(
                request.idempotencyKey().getBytes(StandardCharsets.UTF_8)
        );
        return "fake_" + deterministicId;
    }
}

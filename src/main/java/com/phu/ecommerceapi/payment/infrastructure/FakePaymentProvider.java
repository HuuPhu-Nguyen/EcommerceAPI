package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentProvider;
import com.phu.ecommerceapi.payment.application.PaymentProviderCapabilities;
import com.phu.ecommerceapi.payment.application.PaymentProviderOutcomeMetadata;
import com.phu.ecommerceapi.payment.application.PaymentProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class FakePaymentProvider implements PaymentProvider {

    private static final String DECLINED_CODE = "fake_declined";
    private static final PaymentProviderCapabilities CAPABILITIES = new PaymentProviderCapabilities(
            Set.of("USD"),
            new BigDecimal("0.50"),
            new BigDecimal("999999.99"),
            true,
            true,
            true,
            null
    );

    private final ConcurrentMap<String, PaymentProviderResult> processedRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PaymentRefundProviderResult> processedRefundRequests = new ConcurrentHashMap<>();

    @Override
    public String providerCode() {
        return "fake";
    }

    @Override
    public PaymentProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public PaymentProviderResult createPayment(PaymentProviderRequest request) {
        PaymentProviderResult existingResult = processedRequests.get(request.idempotencyKey());
        if (existingResult != null) {
            return duplicate(existingResult);
        }

        PaymentProviderResult result = switch (requestedOutcome(request)) {
            case PaymentProviderOutcomeMetadata.OUTCOME_SUCCESS -> PaymentProviderResult.succeeded(
                    providerPaymentId(request),
                    "Fake payment approved"
            );
            case PaymentProviderOutcomeMetadata.OUTCOME_FAILURE -> PaymentProviderResult.failed(
                    providerPaymentId(request),
                    DECLINED_CODE,
                    "Fake payment declined"
            );
            case PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT -> throw new PaymentProviderTimeoutException(
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

    @Override
    public PaymentRefundProviderResult refundPayment(PaymentRefundProviderRequest request) {
        PaymentRefundProviderResult existingResult = processedRefundRequests.get(request.idempotencyKey());
        if (existingResult != null) {
            return refundDuplicate(existingResult);
        }

        PaymentRefundProviderResult result = switch (requestedOutcome(request.metadata())) {
            case PaymentProviderOutcomeMetadata.OUTCOME_SUCCESS -> PaymentRefundProviderResult.succeeded(
                    providerRefundId(request),
                    "Fake refund approved"
            );
            case PaymentProviderOutcomeMetadata.OUTCOME_FAILURE -> PaymentRefundProviderResult.failed(
                    providerRefundId(request),
                    DECLINED_CODE,
                    "Fake refund declined"
            );
            case PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT -> throw new PaymentProviderTimeoutException(
                    "Fake refund provider timed out for payment " + request.paymentId()
            );
            default -> throw new IllegalArgumentException("Unsupported fake refund outcome");
        };

        PaymentRefundProviderResult previousResult = processedRefundRequests.putIfAbsent(
                request.idempotencyKey(),
                result
        );
        if (previousResult != null) {
            return refundDuplicate(previousResult);
        }
        return result;
    }

    private PaymentProviderResult duplicate(PaymentProviderResult existingResult) {
        return PaymentProviderResult.duplicate(
                existingResult.providerPaymentId(),
                "Duplicate fake provider request"
        );
    }

    private PaymentRefundProviderResult refundDuplicate(PaymentRefundProviderResult existingResult) {
        return PaymentRefundProviderResult.duplicate(
                existingResult.providerRefundId(),
                "Duplicate fake provider refund request"
        );
    }

    private String requestedOutcome(PaymentProviderRequest request) {
        return requestedOutcome(request.metadata());
    }

    private String requestedOutcome(Map<String, String> metadata) {
        String outcome = metadata.getOrDefault(
                PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                PaymentProviderOutcomeMetadata.OUTCOME_SUCCESS
        );
        return switch (outcome.trim().toLowerCase(Locale.ROOT)) {
            case "success", "succeeded", "approved" -> PaymentProviderOutcomeMetadata.OUTCOME_SUCCESS;
            case "failure", "failed", "declined" -> PaymentProviderOutcomeMetadata.OUTCOME_FAILURE;
            case "timeout" -> PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT;
            default -> throw new IllegalArgumentException("Unsupported fake payment outcome: " + outcome);
        };
    }

    private String providerPaymentId(PaymentProviderRequest request) {
        UUID deterministicId = UUID.nameUUIDFromBytes(
                request.idempotencyKey().getBytes(StandardCharsets.UTF_8)
        );
        return "fake_" + deterministicId;
    }

    private String providerRefundId(PaymentRefundProviderRequest request) {
        UUID deterministicId = UUID.nameUUIDFromBytes(
                request.idempotencyKey().getBytes(StandardCharsets.UTF_8)
        );
        return "fake_refund_" + deterministicId;
    }
}

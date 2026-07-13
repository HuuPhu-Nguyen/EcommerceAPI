package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentProviderOutcomeMetadata;
import com.phu.ecommerceapi.payment.application.PaymentProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentProviderStatus;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePaymentProviderTest {

    private final FakePaymentProvider provider = new FakePaymentProvider();

    @Test
    void reportsFakeProviderCapabilities() {
        assertThat(provider.providerCode()).isEqualTo("fake");
        assertThat(provider.capabilities().supportedCurrencies()).containsExactlyInAnyOrder("EUR", "USD");
        assertThat(provider.capabilities().minimumAmount()).isEqualByComparingTo("0.50");
        assertThat(provider.capabilities().supportsPayments()).isTrue();
        assertThat(provider.capabilities().supportsRefunds()).isTrue();
        assertThat(provider.capabilities().available()).isTrue();
    }

    @Test
    void createsSuccessfulPaymentByDefault() {
        var result = provider.createPayment(request("payment-key-1", Map.of()));

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(result.providerPaymentId()).startsWith("fake_");
        assertThat(result.failureCode()).isNull();
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    void modelsProviderFailure() {
        var result = provider.createPayment(request(
                "payment-key-2",
                Map.of(
                        PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                        PaymentProviderOutcomeMetadata.OUTCOME_FAILURE
                )
        ));

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("fake_declined");
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void modelsProviderTimeoutWithoutRecordingTheRequestAsProcessed() {
        PaymentProviderRequest timeoutRequest = request(
                "payment-key-3",
                Map.of(
                        PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                        PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT
                )
        );

        assertThatThrownBy(() -> provider.createPayment(timeoutRequest))
                .isInstanceOf(PaymentProviderTimeoutException.class);

        var retryResult = provider.createPayment(request("payment-key-3", Map.of()));
        assertThat(retryResult.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
    }

    @Test
    void modelsDuplicateProviderRequestsByIdempotencyKey() {
        var firstResult = provider.createPayment(request("payment-key-4", Map.of()));
        var secondResult = provider.createPayment(request("payment-key-4", Map.of()));

        assertThat(secondResult.status()).isEqualTo(PaymentProviderStatus.DUPLICATE);
        assertThat(secondResult.providerPaymentId()).isEqualTo(firstResult.providerPaymentId());
    }

    @Test
    void modelsSuccessfulRefundByDefault() {
        var result = provider.refundPayment(refundRequest("refund-key-1", Map.of()));

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(result.providerRefundId()).startsWith("fake_refund_");
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void modelsRefundFailure() {
        var result = provider.refundPayment(refundRequest(
                "refund-key-2",
                Map.of(
                        PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                        PaymentProviderOutcomeMetadata.OUTCOME_FAILURE
                )
        ));

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("fake_declined");
    }

    @Test
    void modelsRefundTimeoutWithoutRecordingTheRequestAsProcessed() {
        PaymentRefundProviderRequest timeoutRequest = refundRequest(
                "refund-key-3",
                Map.of(
                        PaymentProviderOutcomeMetadata.OUTCOME_METADATA_KEY,
                        PaymentProviderOutcomeMetadata.OUTCOME_TIMEOUT
                )
        );

        assertThatThrownBy(() -> provider.refundPayment(timeoutRequest))
                .isInstanceOf(PaymentProviderTimeoutException.class);

        var retryResult = provider.refundPayment(refundRequest("refund-key-3", Map.of()));
        assertThat(retryResult.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
    }

    @Test
    void modelsDuplicateRefundRequestsByIdempotencyKey() {
        var firstResult = provider.refundPayment(refundRequest("refund-key-4", Map.of()));
        var secondResult = provider.refundPayment(refundRequest("refund-key-4", Map.of()));

        assertThat(secondResult.status()).isEqualTo(PaymentProviderStatus.DUPLICATE);
        assertThat(secondResult.providerRefundId()).isEqualTo(firstResult.providerRefundId());
    }

    private PaymentProviderRequest request(String idempotencyKey, Map<String, String> metadata) {
        return new PaymentProviderRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new BigDecimal("10.00"),
                "usd",
                idempotencyKey,
                metadata
        );
    }

    private PaymentRefundProviderRequest refundRequest(String idempotencyKey, Map<String, String> metadata) {
        return new PaymentRefundProviderRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "fake_00000000-0000-0000-0000-000000000001",
                new BigDecimal("10.00"),
                "usd",
                idempotencyKey,
                metadata
        );
    }
}

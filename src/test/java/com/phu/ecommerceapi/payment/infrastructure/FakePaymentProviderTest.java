package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentProviderStatus;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePaymentProviderTest {

    private final FakePaymentProvider provider = new FakePaymentProvider();

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
                Map.of(FakePaymentProvider.OUTCOME_METADATA_KEY, FakePaymentProvider.OUTCOME_FAILURE)
        ));

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("fake_declined");
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void modelsProviderTimeoutWithoutRecordingTheRequestAsProcessed() {
        PaymentProviderRequest timeoutRequest = request(
                "payment-key-3",
                Map.of(FakePaymentProvider.OUTCOME_METADATA_KEY, FakePaymentProvider.OUTCOME_TIMEOUT)
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

    private PaymentProviderRequest request(String idempotencyKey, Map<String, String> metadata) {
        return new PaymentProviderRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new BigDecimal("10.00"),
                "usd",
                idempotencyKey,
                metadata
        );
    }
}

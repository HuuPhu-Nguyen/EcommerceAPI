package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.PaymentProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.PaymentProviderStatus;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePaymentProviderTest {

    @Test
    void reportsStripeCapabilitiesWhenGatewayIsAvailable() {
        StripePaymentProvider provider = provider(gatewayWithPayment(request ->
                new StripePaymentIntentResult("pi_capabilities", "succeeded", null)));

        var capabilities = provider.capabilities();

        assertThat(capabilities.supportedCurrencies()).containsExactly("USD");
        assertThat(capabilities.minimumAmount()).isEqualByComparingTo("0.50");
        assertThat(capabilities.maximumAmount()).isEqualByComparingTo("999999.99");
        assertThat(capabilities.supportsPayments()).isTrue();
        assertThat(capabilities.supportsRefunds()).isTrue();
        assertThat(capabilities.available()).isTrue();
        assertThat(capabilities.unavailableReason()).isNull();
    }

    @Test
    void reportsUnavailableWhenGatewayIsMissing() {
        StripePaymentProvider provider = provider(null);

        var capabilities = provider.capabilities();

        assertThat(capabilities.available()).isFalse();
        assertThat(capabilities.unavailableReason()).isEqualTo("Stripe gateway is not configured");
    }

    @Test
    void convertsUsdAmountToMinorUnitsExactly() {
        assertThat(StripePaymentProvider.amountToMinorUnits(new BigDecimal("0.50"), "usd")).isEqualTo(50L);
        assertThat(StripePaymentProvider.amountToMinorUnits(new BigDecimal("20.01"), "USD")).isEqualTo(2001L);
        assertThat(StripePaymentProvider.amountToMinorUnits(new BigDecimal("999999.99"), "USD"))
                .isEqualTo(99999999L);
    }

    @Test
    void rejectsUnsupportedCurrencyAndSubCentAmount() {
        assertThatThrownBy(() -> StripePaymentProvider.amountToMinorUnits(new BigDecimal("20.00"), "EUR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stripe provider currently supports USD only");

        assertThatThrownBy(() -> StripePaymentProvider.amountToMinorUnits(new BigDecimal("20.001"), "USD"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void mapsStripePaymentIntentStatusesToProviderResults() {
        assertThat(mapStatus("succeeded", null).status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);

        PaymentProviderResult declined = mapStatus("requires_payment_method", "card_declined");
        assertThat(declined.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(declined.providerStatus()).isEqualTo("requires_payment_method");
        assertThat(declined.failureCode()).isEqualTo("card_declined");

        assertThat(mapStatus("canceled", null).status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(mapStatus("processing", null).status()).isEqualTo(PaymentProviderStatus.PENDING);
        assertThat(mapStatus("requires_action", null).status()).isEqualTo(PaymentProviderStatus.PENDING);
        assertThat(mapStatus("requires_capture", null).status()).isEqualTo(PaymentProviderStatus.PENDING);
    }

    @Test
    void mapsStripeRefundStatusesToProviderResults() {
        assertThat(mapRefundStatus("succeeded", null).status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);

        PaymentRefundProviderResult failed = mapRefundStatus("failed", "stripe_expired_or_canceled_card");
        assertThat(failed.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("stripe_expired_or_canceled_card");

        assertThat(mapRefundStatus("canceled", null).status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(mapRefundStatus("pending", null).status()).isEqualTo(PaymentProviderStatus.PENDING);
        assertThat(mapRefundStatus("requires_action", null).status()).isEqualTo(PaymentProviderStatus.PENDING);
    }

    @Test
    void createsPaymentIntentRequestWithMetadataAndProviderIdempotencyKey() {
        AtomicReference<StripePaymentIntentCreateRequest> capturedRequest = new AtomicReference<>();
        StripePaymentProvider provider = provider(gatewayWithPayment(request -> {
            capturedRequest.set(request);
            return new StripePaymentIntentResult("pi_created", "succeeded", null);
        }));

        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentProviderResult result = provider.createPayment(new PaymentProviderRequest(
                paymentId,
                orderId,
                new BigDecimal("42.15"),
                "USD",
                "payment:stripe:42:%s:payment-key".formatted(orderId),
                Map.of("paymentMethodToken", "pm_card_visa")
        ));

        StripePaymentIntentCreateRequest request = capturedRequest.get();
        assertThat(result.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(result.providerPaymentId()).isEqualTo("pi_created");
        assertThat(result.providerStatus()).isEqualTo("succeeded");
        assertThat(request.paymentId()).isEqualTo(paymentId);
        assertThat(request.orderId()).isEqualTo(orderId);
        assertThat(request.amountMinorUnits()).isEqualTo(4215L);
        assertThat(request.currency()).isEqualTo("usd");
        assertThat(request.paymentMethodToken()).isEqualTo("pm_card_visa");
        assertThat(request.idempotencyKey()).isEqualTo("payment:stripe:42:%s:payment-key".formatted(orderId));
        assertThat(request.metadata()).containsEntry("internalPaymentId", paymentId.toString());
        assertThat(request.metadata()).containsEntry("orderId", orderId.toString());
        assertThat(request.metadata()).containsEntry("providerCode", "stripe");
        assertThat(request.metadata()).containsEntry("environment", "test");
    }

    @Test
    void createsRefundRequestWithMetadataAndProviderIdempotencyKey() {
        AtomicReference<StripeRefundCreateRequest> capturedRequest = new AtomicReference<>();
        StripePaymentProvider provider = provider(gatewayWithRefund(request -> {
            capturedRequest.set(request);
            return new StripeRefundResult("re_created", "succeeded", null);
        }));

        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentRefundProviderResult result = provider.refundPayment(new PaymentRefundProviderRequest(
                refundId,
                paymentId,
                "pi_original_payment",
                new BigDecimal("42.15"),
                "USD",
                "refund:stripe:42:%s:refund-key".formatted(paymentId),
                Map.of("refundReason", "customer requested")
        ));

        StripeRefundCreateRequest request = capturedRequest.get();
        assertThat(result.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(result.providerRefundId()).isEqualTo("re_created");
        assertThat(request.refundId()).isEqualTo(refundId);
        assertThat(request.paymentId()).isEqualTo(paymentId);
        assertThat(request.paymentIntentId()).isEqualTo("pi_original_payment");
        assertThat(request.amountMinorUnits()).isEqualTo(4215L);
        assertThat(request.currency()).isEqualTo("usd");
        assertThat(request.idempotencyKey()).isEqualTo("refund:stripe:42:%s:refund-key".formatted(paymentId));
        assertThat(request.metadata()).containsEntry("internalRefundId", refundId.toString());
        assertThat(request.metadata()).containsEntry("paymentId", paymentId.toString());
        assertThat(request.metadata()).containsEntry("providerCode", "stripe");
        assertThat(request.metadata()).containsEntry("environment", "test");
    }

    @Test
    void mapsGatewayFailureToStableFailedResultWithoutRawProviderBody() {
        UUID paymentId = UUID.randomUUID();
        StripePaymentProvider provider = provider(gatewayWithPayment(request -> {
            throw new StripePaymentGatewayException(
                    "stripe_api_error",
                    "Stripe payment failed: stripe_api_error",
                    null
            );
        }));

        PaymentProviderResult result = provider.createPayment(request(paymentId));

        assertThat(result.providerPaymentId()).isEqualTo("stripe_error_" + paymentId);
        assertThat(result.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("stripe_api_error");
        assertThat(result.message()).isEqualTo("Stripe payment failed: stripe_api_error");
    }

    @Test
    void mapsRefundGatewayFailureToStableFailedResultWithoutRawProviderBody() {
        UUID refundId = UUID.randomUUID();
        StripePaymentProvider provider = provider(gatewayWithRefund(request -> {
            throw new StripePaymentGatewayException(
                    "stripe_api_error",
                    "Stripe refund failed: stripe_api_error",
                    null
            );
        }));

        PaymentRefundProviderResult result = provider.refundPayment(refundRequest(refundId));

        assertThat(result.providerRefundId()).isEqualTo("stripe_refund_error_" + refundId);
        assertThat(result.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("stripe_api_error");
        assertThat(result.message()).isEqualTo("Stripe refund failed: stripe_api_error");
    }

    @Test
    void propagatesGatewayTimeoutForUseCaseTimeoutStateHandling() {
        StripePaymentProvider provider = provider(gatewayWithPayment(request -> {
            throw new PaymentProviderTimeoutException("Stripe payment provider timed out");
        }));

        assertThatThrownBy(() -> provider.createPayment(request(UUID.randomUUID())))
                .isInstanceOf(PaymentProviderTimeoutException.class)
                .hasMessage("Stripe payment provider timed out");
    }

    @Test
    void propagatesRefundGatewayTimeoutForUseCaseTimeoutStateHandling() {
        StripePaymentProvider provider = provider(gatewayWithRefund(request -> {
            throw new PaymentProviderTimeoutException("Stripe refund provider timed out");
        }));

        assertThatThrownBy(() -> provider.refundPayment(refundRequest(UUID.randomUUID())))
                .isInstanceOf(PaymentProviderTimeoutException.class)
                .hasMessage("Stripe refund provider timed out");
    }

    private PaymentProviderResult mapStatus(String status, String failureCode) {
        return StripePaymentProvider.mapPaymentIntentResult(
                new StripePaymentIntentResult("pi_status_" + status, status, failureCode)
        );
    }

    private PaymentRefundProviderResult mapRefundStatus(String status, String failureCode) {
        return StripePaymentProvider.mapRefundResult(
                new StripeRefundResult("re_status_" + status, status, failureCode)
        );
    }

    private PaymentProviderRequest request(UUID paymentId) {
        return new PaymentProviderRequest(
                paymentId,
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "USD",
                "payment:stripe:42:%s:key".formatted(UUID.randomUUID()),
                Map.of("paymentMethodToken", "pm_card_visa")
        );
    }

    private PaymentRefundProviderRequest refundRequest(UUID refundId) {
        return new PaymentRefundProviderRequest(
                refundId,
                UUID.randomUUID(),
                "pi_original_payment",
                new BigDecimal("10.00"),
                "USD",
                "refund:stripe:42:%s:key".formatted(UUID.randomUUID()),
                Map.of("refundReason", "customer requested")
        );
    }

    private StripePaymentProvider provider(StripePaymentGateway gateway) {
        return new StripePaymentProvider(objectProvider(gateway), appProperties());
    }

    private StripePaymentGateway gatewayWithPayment(
            Function<StripePaymentIntentCreateRequest, StripePaymentIntentResult> paymentHandler
    ) {
        return new StubStripePaymentGateway(
                paymentHandler,
                request -> {
                    throw new UnsupportedOperationException("Refund gateway is not stubbed");
                }
        );
    }

    private StripePaymentGateway gatewayWithRefund(
            Function<StripeRefundCreateRequest, StripeRefundResult> refundHandler
    ) {
        return new StubStripePaymentGateway(
                request -> {
                    throw new UnsupportedOperationException("Payment gateway is not stubbed");
                },
                refundHandler
        );
    }

    private ObjectProvider<StripePaymentGateway> objectProvider(StripePaymentGateway gateway) {
        return new ObjectProvider<>() {
            @Override
            public StripePaymentGateway getIfAvailable() {
                return gateway;
            }
        };
    }

    private AppProperties appProperties() {
        return new AppProperties(
                "test",
                "keycloak",
                new AppProperties.PaymentProviderProperties("stripe", List.of("stripe")),
                new AppProperties.FakeProvider("fake-webhook-secret"),
                new AppProperties.StripeProviderProperties(
                        "sk_test_safe_placeholder",
                        "whsec_test_safe_placeholder",
                        "",
                        2000,
                        5000
                )
        );
    }

    private static final class StubStripePaymentGateway implements StripePaymentGateway {

        private final Function<StripePaymentIntentCreateRequest, StripePaymentIntentResult> paymentHandler;
        private final Function<StripeRefundCreateRequest, StripeRefundResult> refundHandler;

        private StubStripePaymentGateway(
                Function<StripePaymentIntentCreateRequest, StripePaymentIntentResult> paymentHandler,
                Function<StripeRefundCreateRequest, StripeRefundResult> refundHandler
        ) {
            this.paymentHandler = paymentHandler;
            this.refundHandler = refundHandler;
        }

        @Override
        public StripePaymentIntentResult createPaymentIntent(StripePaymentIntentCreateRequest request) {
            return paymentHandler.apply(request);
        }

        @Override
        public StripeRefundResult createRefund(StripeRefundCreateRequest request) {
            return refundHandler.apply(request);
        }
    }
}

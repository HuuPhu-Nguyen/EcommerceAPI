package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeClientPaymentGatewayTest {

    @Test
    void createsConfirmedCardPaymentIntentWithTimeoutsAndIdempotencyKey() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.paymentIntent = paymentIntent("pi_client_success", "succeeded", null);
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                client,
                unusedRefundClient(),
                appProperties()
        );
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        StripePaymentIntentResult result = gateway.createPaymentIntent(new StripePaymentIntentCreateRequest(
                paymentId,
                orderId,
                2050L,
                "USD",
                "pm_card_visa",
                "payment:stripe:42:%s:key".formatted(orderId),
                Map.of("internalPaymentId", paymentId.toString(), "providerCode", "stripe")
        ));

        assertThat(result.paymentIntentId()).isEqualTo("pi_client_success");
        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(client.params.getAmount()).isEqualTo(2050L);
        assertThat(client.params.getCurrency()).isEqualTo("usd");
        assertThat(client.params.getPaymentMethod()).isEqualTo("pm_card_visa");
        assertThat(client.params.getConfirm()).isTrue();
        assertThat(client.params.getPaymentMethodTypes()).containsExactly("card");
        assertThat(client.params.getMetadata()).containsEntry("internalPaymentId", paymentId.toString());
        assertThat(client.options.getIdempotencyKey()).isEqualTo("payment:stripe:42:%s:key".formatted(orderId));
        assertThat(client.options.getConnectTimeout()).isEqualTo(1234);
        assertThat(client.options.getReadTimeout()).isEqualTo(5678);
        assertThat(RequestOptions.unsafeGetStripeVersionOverride(client.options)).isEqualTo("2024-06-20");
    }

    @Test
    void mapsPaymentIntentLastPaymentErrorToResultFailureCode() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.paymentIntent = paymentIntent("pi_client_declined", "requires_payment_method", "insufficient_funds");
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                client,
                unusedRefundClient(),
                appProperties()
        );

        StripePaymentIntentResult result = gateway.createPaymentIntent(request());

        assertThat(result.paymentIntentId()).isEqualTo("pi_client_declined");
        assertThat(result.status()).isEqualTo("requires_payment_method");
        assertThat(result.failureCode()).isEqualTo("stripe_insufficient_funds");
    }

    @Test
    void mapsApiConnectionExceptionToProviderTimeout() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.exception = new ApiConnectionException("read timed out");
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                client,
                unusedRefundClient(),
                appProperties()
        );

        assertThatThrownBy(() -> gateway.createPaymentIntent(request()))
                .isInstanceOf(PaymentProviderTimeoutException.class)
                .hasMessageContaining("Stripe payment provider timed out for payment");
    }

    @Test
    void mapsCardExceptionToStableFailureCode() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.exception = new CardException(
                "card declined",
                "req_test",
                "card_declined",
                "number",
                "insufficient_funds",
                "ch_test",
                402,
                null
        );
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                client,
                unusedRefundClient(),
                appProperties()
        );

        assertThatThrownBy(() -> gateway.createPaymentIntent(request()))
                .isInstanceOf(StripePaymentGatewayException.class)
                .hasMessage("Stripe payment failed: stripe_insufficient_funds")
                .extracting(exception -> ((StripePaymentGatewayException) exception).failureCode())
                .isEqualTo("stripe_insufficient_funds");
    }

    @Test
    void mapsApiExceptionToStableFailureCode() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.exception = new ApiException("rate limited", "req_test", "rate_limit", 429, null);
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                client,
                unusedRefundClient(),
                appProperties()
        );

        assertThatThrownBy(() -> gateway.createPaymentIntent(request()))
                .isInstanceOf(StripePaymentGatewayException.class)
                .hasMessage("Stripe payment failed: stripe_rate_limit")
                .extracting(exception -> ((StripePaymentGatewayException) exception).failureCode())
                .isEqualTo("stripe_rate_limit");
    }

    @Test
    void createsRefundWithPaymentIntentTimeoutsAndIdempotencyKey() {
        CapturingRefundClient refundClient = new CapturingRefundClient();
        refundClient.refund = refund("re_client_success", "succeeded", null);
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                unusedPaymentIntentClient(),
                refundClient,
                appProperties()
        );
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        StripeRefundResult result = gateway.createRefund(new StripeRefundCreateRequest(
                refundId,
                paymentId,
                "pi_original_payment",
                2050L,
                "USD",
                "refund:stripe:42:%s:key".formatted(paymentId),
                Map.of("internalRefundId", refundId.toString(), "providerCode", "stripe")
        ));

        assertThat(result.refundId()).isEqualTo("re_client_success");
        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(refundClient.params.getPaymentIntent()).isEqualTo("pi_original_payment");
        assertThat(refundClient.params.getAmount()).isEqualTo(2050L);
        Map<String, String> metadata = refundMetadata(refundClient.params);
        assertThat(metadata).containsEntry("internalRefundId", refundId.toString());
        assertThat(refundClient.options.getIdempotencyKey()).isEqualTo("refund:stripe:42:%s:key".formatted(paymentId));
        assertThat(refundClient.options.getConnectTimeout()).isEqualTo(1234);
        assertThat(refundClient.options.getReadTimeout()).isEqualTo(5678);
        assertThat(RequestOptions.unsafeGetStripeVersionOverride(refundClient.options)).isEqualTo("2024-06-20");
    }

    @Test
    void blankStripeApiVersionUsesSdkDefaultRequestVersion() {
        StripeRequestOptionsFactory factory = new StripeRequestOptionsFactory(appProperties(""));

        RequestOptions requestOptions = factory.requestOptions("stripe:test:key");

        assertThat(requestOptions.getIdempotencyKey()).isEqualTo("stripe:test:key");
        assertThat(requestOptions.getConnectTimeout()).isEqualTo(1234);
        assertThat(requestOptions.getReadTimeout()).isEqualTo(5678);
        assertThat(RequestOptions.unsafeGetStripeVersionOverride(requestOptions)).isNull();
    }

    @Test
    void providerReadRequestOptionsApplyConfiguredApiVersionWithoutIdempotencyKey() {
        StripeRequestOptionsFactory factory = new StripeRequestOptionsFactory(appProperties());

        RequestOptions requestOptions = factory.requestOptions();

        assertThat(requestOptions.getIdempotencyKey()).isNull();
        assertThat(requestOptions.getConnectTimeout()).isEqualTo(1234);
        assertThat(requestOptions.getReadTimeout()).isEqualTo(5678);
        assertThat(RequestOptions.unsafeGetStripeVersionOverride(requestOptions)).isEqualTo("2024-06-20");
    }

    @Test
    void mapsRefundFailureReasonToResultFailureCode() {
        CapturingRefundClient refundClient = new CapturingRefundClient();
        refundClient.refund = refund("re_client_failed", "failed", "expired_or_canceled_card");
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                unusedPaymentIntentClient(),
                refundClient,
                appProperties()
        );

        StripeRefundResult result = gateway.createRefund(refundRequest());

        assertThat(result.refundId()).isEqualTo("re_client_failed");
        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.failureCode()).isEqualTo("stripe_expired_or_canceled_card");
    }

    @Test
    void mapsRefundApiConnectionExceptionToProviderTimeout() {
        CapturingRefundClient refundClient = new CapturingRefundClient();
        refundClient.exception = new ApiConnectionException("read timed out");
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                unusedPaymentIntentClient(),
                refundClient,
                appProperties()
        );

        assertThatThrownBy(() -> gateway.createRefund(refundRequest()))
                .isInstanceOf(PaymentProviderTimeoutException.class)
                .hasMessageContaining("Stripe refund provider timed out for payment");
    }

    @Test
    void mapsRefundApiExceptionToStableFailureCode() {
        CapturingRefundClient refundClient = new CapturingRefundClient();
        refundClient.exception = new ApiException("rate limited", "req_test", "rate_limit", 429, null);
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(
                unusedPaymentIntentClient(),
                refundClient,
                appProperties()
        );

        assertThatThrownBy(() -> gateway.createRefund(refundRequest()))
                .isInstanceOf(StripePaymentGatewayException.class)
                .hasMessage("Stripe refund failed: stripe_rate_limit")
                .extracting(exception -> ((StripePaymentGatewayException) exception).failureCode())
                .isEqualTo("stripe_rate_limit");
    }

    private StripePaymentIntentCreateRequest request() {
        return new StripePaymentIntentCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1000L,
                "USD",
                "pm_card_visa",
                "payment:stripe:42:test:key",
                Map.of("providerCode", "stripe")
        );
    }

    private StripeRefundCreateRequest refundRequest() {
        return new StripeRefundCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "pi_original_payment",
                1000L,
                "USD",
                "refund:stripe:42:test:key",
                Map.of("providerCode", "stripe")
        );
    }

    private PaymentIntent paymentIntent(String id, String status, String declineCode) {
        PaymentIntent paymentIntent = new PaymentIntent();
        paymentIntent.setId(id);
        paymentIntent.setStatus(status);
        if (declineCode != null) {
            StripeError stripeError = new StripeError();
            stripeError.setDeclineCode(declineCode);
            stripeError.setCode("card_declined");
            paymentIntent.setLastPaymentError(stripeError);
        }
        return paymentIntent;
    }

    private Refund refund(String id, String status, String failureReason) {
        Refund refund = new Refund();
        refund.setId(id);
        refund.setStatus(status);
        refund.setFailureReason(failureReason);
        return refund;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> refundMetadata(RefundCreateParams params) {
        return (Map<String, String>) params.getMetadata();
    }

    private StripePaymentIntentClient unusedPaymentIntentClient() {
        return (params, options) -> {
            throw new UnsupportedOperationException("PaymentIntent client is not stubbed");
        };
    }

    private StripeRefundClient unusedRefundClient() {
        return (params, options) -> {
            throw new UnsupportedOperationException("Refund client is not stubbed");
        };
    }

    private AppProperties appProperties() {
        return appProperties("2024-06-20");
    }

    private AppProperties appProperties(String apiVersion) {
        return new AppProperties(
                "test",
                "keycloak",
                new AppProperties.PaymentProviderProperties("stripe", List.of("stripe")),
                new AppProperties.FakeProvider("fake-webhook-secret"),
                new AppProperties.StripeProviderProperties(
                        "sk_test_safe_placeholder",
                        "whsec_test_safe_placeholder",
                        apiVersion,
                        1234,
                        5678
                )
        );
    }

    private static final class CapturingPaymentIntentClient implements StripePaymentIntentClient {

        private PaymentIntent paymentIntent;
        private StripeException exception;
        private PaymentIntentCreateParams params;
        private RequestOptions options;

        @Override
        public PaymentIntent create(PaymentIntentCreateParams params, RequestOptions options) throws StripeException {
            this.params = params;
            this.options = options;
            if (exception != null) {
                throw exception;
            }
            return paymentIntent;
        }
    }

    private static final class CapturingRefundClient implements StripeRefundClient {

        private Refund refund;
        private StripeException exception;
        private RefundCreateParams params;
        private RequestOptions options;

        @Override
        public Refund create(RefundCreateParams params, RequestOptions options) throws StripeException {
            this.params = params;
            this.options = options;
            if (exception != null) {
                throw exception;
            }
            return refund;
        }
    }
}

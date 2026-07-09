package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
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
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(client, appProperties());
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
    }

    @Test
    void mapsPaymentIntentLastPaymentErrorToResultFailureCode() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.paymentIntent = paymentIntent("pi_client_declined", "requires_payment_method", "insufficient_funds");
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(client, appProperties());

        StripePaymentIntentResult result = gateway.createPaymentIntent(request());

        assertThat(result.paymentIntentId()).isEqualTo("pi_client_declined");
        assertThat(result.status()).isEqualTo("requires_payment_method");
        assertThat(result.failureCode()).isEqualTo("stripe_insufficient_funds");
    }

    @Test
    void mapsApiConnectionExceptionToProviderTimeout() {
        CapturingPaymentIntentClient client = new CapturingPaymentIntentClient();
        client.exception = new ApiConnectionException("read timed out");
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(client, appProperties());

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
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(client, appProperties());

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
        StripeClientPaymentGateway gateway = new StripeClientPaymentGateway(client, appProperties());

        assertThatThrownBy(() -> gateway.createPaymentIntent(request()))
                .isInstanceOf(StripePaymentGatewayException.class)
                .hasMessage("Stripe payment failed: stripe_rate_limit")
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
}

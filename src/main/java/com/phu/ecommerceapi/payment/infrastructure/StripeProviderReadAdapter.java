package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.StripePaymentIntentSnapshot;
import com.phu.ecommerceapi.payment.application.StripeProviderReadPort;
import com.phu.ecommerceapi.payment.application.StripeRefundSnapshot;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@ConditionalOnBean(StripeClient.class)
class StripeProviderReadAdapter implements StripeProviderReadPort {

    private final StripeClient stripeClient;
    private final StripeRequestOptionsFactory requestOptionsFactory;

    StripeProviderReadAdapter(StripeClient stripeClient, AppProperties appProperties) {
        this.stripeClient = stripeClient;
        this.requestOptionsFactory = new StripeRequestOptionsFactory(appProperties);
    }

    @Override
    public Optional<StripePaymentIntentSnapshot> fetchPaymentIntent(String providerPaymentId) {
        try {
            PaymentIntent paymentIntent = stripeClient.v1()
                    .paymentIntents()
                    .retrieve(providerPaymentId, requestOptions());
            return Optional.of(new StripePaymentIntentSnapshot(
                    paymentIntent.getId(),
                    paymentIntent.getStatus(),
                    failureCode(paymentIntent),
                    "Stripe PaymentIntent current status is " + paymentIntent.getStatus()
            ));
        } catch (StripeException exception) {
            if (isNotFound(exception)) {
                return Optional.empty();
            }
            throw new IllegalStateException("Stripe PaymentIntent read failed: " + failureCode(exception), exception);
        }
    }

    @Override
    public Optional<StripeRefundSnapshot> fetchRefund(String providerRefundId) {
        try {
            Refund refund = stripeClient.v1()
                    .refunds()
                    .retrieve(providerRefundId, requestOptions());
            return Optional.of(new StripeRefundSnapshot(
                    refund.getId(),
                    refund.getStatus(),
                    normalizeFailureCode(refund.getFailureReason(), refund.getStatus(), "refund_failed"),
                    "Stripe refund current status is " + refund.getStatus()
            ));
        } catch (StripeException exception) {
            if (isNotFound(exception)) {
                return Optional.empty();
            }
            throw new IllegalStateException("Stripe refund read failed: " + failureCode(exception), exception);
        }
    }

    private RequestOptions requestOptions() {
        return requestOptionsFactory.requestOptions();
    }

    private String failureCode(PaymentIntent paymentIntent) {
        StripeError error = paymentIntent.getLastPaymentError();
        if (error == null) {
            return null;
        }
        return normalizeFailureCode(error.getDeclineCode(), error.getCode(), "payment_failed");
    }

    private String failureCode(StripeException exception) {
        if (exception.getStatusCode() != null) {
            return normalizeFailureCode(exception.getCode(), "api_" + exception.getStatusCode(), "api_error");
        }
        return normalizeFailureCode(exception.getCode(), exception.getClass().getSimpleName(), "api_error");
    }

    private boolean isNotFound(StripeException exception) {
        return Integer.valueOf(404).equals(exception.getStatusCode());
    }

    private String normalizeFailureCode(String... values) {
        String value = firstText(values);
        if (value == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder("stripe_");
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            } else {
                normalized.append('_');
            }
        }
        return normalized.toString().replaceAll("_+", "_");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.PaymentProviderTimeoutException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import java.util.Locale;

final class StripeClientPaymentGateway implements StripePaymentGateway {

    private final StripePaymentIntentClient paymentIntentClient;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    StripeClientPaymentGateway(StripePaymentIntentClient paymentIntentClient, AppProperties appProperties) {
        this.paymentIntentClient = paymentIntentClient;
        this.connectTimeoutMs = appProperties.stripe().connectTimeoutMs();
        this.readTimeoutMs = appProperties.stripe().readTimeoutMs();
    }

    @Override
    public StripePaymentIntentResult createPaymentIntent(StripePaymentIntentCreateRequest request) {
        try {
            PaymentIntent paymentIntent = paymentIntentClient.create(createParams(request), requestOptions(request));
            return new StripePaymentIntentResult(
                    paymentIntent.getId(),
                    paymentIntent.getStatus(),
                    failureCode(paymentIntent)
            );
        } catch (ApiConnectionException exception) {
            throw new PaymentProviderTimeoutException(
                    "Stripe payment provider timed out for payment " + request.paymentId()
            );
        } catch (StripeException exception) {
            String failureCode = failureCode(exception);
            throw new StripePaymentGatewayException(
                    failureCode,
                    "Stripe payment failed: " + failureCode,
                    exception
            );
        }
    }

    private PaymentIntentCreateParams createParams(StripePaymentIntentCreateRequest request) {
        return PaymentIntentCreateParams.builder()
                .setAmount(request.amountMinorUnits())
                .setCurrency(request.currency())
                .setPaymentMethod(request.paymentMethodToken())
                .setConfirm(true)
                .addPaymentMethodType("card")
                .putAllMetadata(request.metadata())
                .build();
    }

    private RequestOptions requestOptions(StripePaymentIntentCreateRequest request) {
        return RequestOptions.builder()
                .setIdempotencyKey(request.idempotencyKey())
                .setConnectTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs)
                .build();
    }

    private String failureCode(PaymentIntent paymentIntent) {
        StripeError error = paymentIntent.getLastPaymentError();
        if (error == null) {
            return null;
        }
        return normalizeFailureCode(error.getDeclineCode(), error.getCode(), "payment_failed");
    }

    private String failureCode(StripeException exception) {
        if (exception instanceof CardException cardException) {
            return normalizeFailureCode(
                    cardException.getDeclineCode(),
                    cardException.getCode(),
                    "card_error"
            );
        }
        if (exception.getStatusCode() != null) {
            return normalizeFailureCode(exception.getCode(), "api_" + exception.getStatusCode(), "api_error");
        }
        return normalizeFailureCode(exception.getCode(), exception.getClass().getSimpleName(), "api_error");
    }

    private String normalizeFailureCode(String preferredCode, String fallbackCode, String defaultCode) {
        String code = firstText(preferredCode, fallbackCode, defaultCode);
        StringBuilder normalized = new StringBuilder("stripe_");
        for (char character : code.trim().toLowerCase(Locale.ROOT).toCharArray()) {
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
                return value;
            }
        }
        return "unknown";
    }
}

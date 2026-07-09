package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.config.AppProperties;
import com.phu.ecommerceapi.payment.application.PaymentProvider;
import com.phu.ecommerceapi.payment.application.PaymentProviderCapabilities;
import com.phu.ecommerceapi.payment.application.PaymentProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentProviderResult;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderRequest;
import com.phu.ecommerceapi.payment.application.PaymentRefundProviderResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class StripePaymentProvider implements PaymentProvider {

    static final BigDecimal MINIMUM_USD_AMOUNT = new BigDecimal("0.50");
    static final BigDecimal MAXIMUM_USD_AMOUNT = new BigDecimal("999999.99");

    private static final String PROVIDER_CODE = "stripe";
    private static final String PAYMENT_METHOD_TOKEN_METADATA_KEY = "paymentMethodToken";

    private final ObjectProvider<StripePaymentGateway> stripeGateway;
    private final AppProperties appProperties;

    public StripePaymentProvider(
            ObjectProvider<StripePaymentGateway> stripeGateway,
            AppProperties appProperties
    ) {
        this.stripeGateway = stripeGateway;
        this.appProperties = appProperties;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public PaymentProviderCapabilities capabilities() {
        boolean available = stripeGateway.getIfAvailable() != null;
        return new PaymentProviderCapabilities(
                Set.of("USD"),
                MINIMUM_USD_AMOUNT,
                MAXIMUM_USD_AMOUNT,
                true,
                true,
                available,
                available ? null : "Stripe gateway is not configured"
        );
    }

    @Override
    public PaymentProviderResult createPayment(PaymentProviderRequest request) {
        try {
            StripePaymentIntentResult result = gateway().createPaymentIntent(new StripePaymentIntentCreateRequest(
                    request.paymentId(),
                    request.orderId(),
                    amountToMinorUnits(request.amount(), request.currency()),
                    request.currency(),
                    paymentMethodToken(request),
                    request.idempotencyKey(),
                    stripeMetadata(request)
            ));
            return mapPaymentIntentResult(result);
        } catch (StripePaymentGatewayException exception) {
            return PaymentProviderResult.failed(
                    fallbackProviderPaymentId(request.paymentId()),
                    exception.failureCode(),
                    exception.getMessage()
            );
        }
    }

    @Override
    public PaymentRefundProviderResult refundPayment(PaymentRefundProviderRequest request) {
        throw new UnsupportedOperationException("Stripe refunds are implemented in TASK-057");
    }

    static long amountToMinorUnits(BigDecimal amount, String currency) {
        if (!"USD".equals(normalizeCurrency(currency))) {
            throw new IllegalArgumentException("Stripe provider currently supports USD only");
        }
        BigDecimal minorUnits = amount
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2);
        if (minorUnits.compareTo(new BigDecimal("99999999")) > 0) {
            throw new IllegalArgumentException("Stripe amount exceeds the eight-digit minor-unit limit");
        }
        return minorUnits.longValueExact();
    }

    static PaymentProviderResult mapPaymentIntentResult(StripePaymentIntentResult result) {
        String stripeStatus = normalizeStripeStatus(result.status());
        return switch (stripeStatus) {
            case "succeeded" -> PaymentProviderResult.succeeded(
                    result.paymentIntentId(),
                    stripeStatus,
                    "Stripe payment succeeded"
            );
            case "requires_payment_method", "canceled" -> PaymentProviderResult.failed(
                    result.paymentIntentId(),
                    stripeStatus,
                    firstText(result.failureCode(), "stripe_" + stripeStatus),
                    "Stripe payment failed: " + stripeStatus
            );
            case "processing", "requires_action", "requires_capture", "requires_confirmation" ->
                    PaymentProviderResult.pending(
                            result.paymentIntentId(),
                            stripeStatus,
                            "Stripe payment is pending: " + stripeStatus
                    );
            default -> PaymentProviderResult.failed(
                    result.paymentIntentId(),
                    stripeStatus,
                    "stripe_unmapped_status",
                    "Stripe payment status is unsupported: " + stripeStatus
            );
        };
    }

    private StripePaymentGateway gateway() {
        StripePaymentGateway gateway = stripeGateway.getIfAvailable();
        if (gateway == null) {
            throw new IllegalStateException("Stripe gateway is not configured");
        }
        return gateway;
    }

    private String paymentMethodToken(PaymentProviderRequest request) {
        String token = request.metadata().get(PAYMENT_METHOD_TOKEN_METADATA_KEY);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Stripe payment method token is required");
        }
        return token.trim();
    }

    private Map<String, String> stripeMetadata(PaymentProviderRequest request) {
        return Map.of(
                "internalPaymentId", request.paymentId().toString(),
                "orderId", request.orderId().toString(),
                "providerCode", providerCode(),
                "environment", appProperties.environment()
        );
    }

    private String fallbackProviderPaymentId(UUID paymentId) {
        return "stripe_error_" + paymentId;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Stripe currency is required");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeStripeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstText(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }
}

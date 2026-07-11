package com.phu.ecommerceapi.shared.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final BusinessMetrics businessMetrics = new BusinessMetrics(meterRegistry);

    @Test
    void recordsBusinessMetricCountersWithStableTags() {
        businessMetrics.checkoutAttempt("SUCCESS");
        businessMetrics.paymentOutcome("stripe", "SUCCEEDED");
        businessMetrics.refundOutcome("fake", "FAILED");
        businessMetrics.providerWebhook("stripe", "PROCESSED");
        businessMetrics.stripeProviderOperation("payment", "TIMEOUT");
        businessMetrics.idempotencyDecision("REPLAY");
        businessMetrics.ledgerPosting("PAYMENT_CAPTURE", "SUCCESS");
        businessMetrics.auditWrite("PAYMENT_SUCCEEDED", "SUCCESS");

        assertThat(counter("app.checkout.attempts", "outcome", "success")).isEqualTo(1.0);
        assertThat(counter("app.payment.outcomes", "provider", "stripe", "status", "succeeded")).isEqualTo(1.0);
        assertThat(counter("app.refund.outcomes", "provider", "fake", "status", "failed")).isEqualTo(1.0);
        assertThat(counter("app.provider_webhook.processed", "provider", "stripe", "status", "processed"))
                .isEqualTo(1.0);
        assertThat(counter(
                "app.payment_provider.operations",
                "provider",
                "stripe",
                "operation",
                "payment",
                "outcome",
                "timeout"
        )).isEqualTo(1.0);
        assertThat(counter("app.idempotency.decisions", "decision", "replay")).isEqualTo(1.0);
        assertThat(counter("app.ledger.postings", "type", "payment_capture", "status", "success")).isEqualTo(1.0);
        assertThat(counter("app.audit.writes", "action", "payment_succeeded", "status", "success")).isEqualTo(1.0);
        assertThat(meterRegistry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain(
                        "paymentId",
                        "refundId",
                        "orderId",
                        "providerPaymentId",
                        "providerRefundId",
                        "idempotencyKey"
                );
    }

    private double counter(String name, String tagName, String tagValue) {
        return meterRegistry.get(name)
                .tag(tagName, tagValue)
                .counter()
                .count();
    }

    private double counter(
            String name,
            String firstTagName,
            String firstTagValue,
            String secondTagName,
            String secondTagValue
    ) {
        return meterRegistry.get(name)
                .tag(firstTagName, firstTagValue)
                .tag(secondTagName, secondTagValue)
                .counter()
                .count();
    }

    private double counter(
            String name,
            String firstTagName,
            String firstTagValue,
            String secondTagName,
            String secondTagValue,
            String thirdTagName,
            String thirdTagValue
    ) {
        return meterRegistry.get(name)
                .tag(firstTagName, firstTagValue)
                .tag(secondTagName, secondTagValue)
                .tag(thirdTagName, thirdTagValue)
                .counter()
                .count();
    }
}

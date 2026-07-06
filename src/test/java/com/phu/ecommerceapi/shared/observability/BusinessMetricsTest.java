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
        businessMetrics.paymentOutcome("SUCCEEDED");
        businessMetrics.refundOutcome("FAILED");
        businessMetrics.idempotencyDecision("REPLAY");
        businessMetrics.ledgerPosting("PAYMENT_CAPTURE", "SUCCESS");
        businessMetrics.auditWrite("PAYMENT_SUCCEEDED", "SUCCESS");

        assertThat(counter("app.checkout.attempts", "outcome", "success")).isEqualTo(1.0);
        assertThat(counter("app.payment.outcomes", "status", "succeeded")).isEqualTo(1.0);
        assertThat(counter("app.refund.outcomes", "status", "failed")).isEqualTo(1.0);
        assertThat(counter("app.idempotency.decisions", "decision", "replay")).isEqualTo(1.0);
        assertThat(counter("app.ledger.postings", "type", "payment_capture", "status", "success")).isEqualTo(1.0);
        assertThat(counter("app.audit.writes", "action", "payment_succeeded", "status", "success")).isEqualTo(1.0);
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
}

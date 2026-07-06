package com.phu.ecommerceapi.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void checkoutAttempt(String outcome) {
        increment("app.checkout.attempts", "outcome", outcome);
    }

    public void paymentOutcome(String status) {
        increment("app.payment.outcomes", "status", status);
    }

    public void refundOutcome(String status) {
        increment("app.refund.outcomes", "status", status);
    }

    public void idempotencyDecision(String decision) {
        increment("app.idempotency.decisions", "decision", decision);
    }

    public void ledgerPosting(String type, String status) {
        Counter.builder("app.ledger.postings")
                .description("Ledger posting attempts by type and outcome")
                .tag("type", normalize(type))
                .tag("status", normalize(status))
                .register(meterRegistry)
                .increment();
    }

    public void auditWrite(String action, String status) {
        Counter.builder("app.audit.writes")
                .description("Audit event write attempts by action and outcome")
                .tag("action", normalize(action))
                .tag("status", normalize(status))
                .register(meterRegistry)
                .increment();
    }

    private void increment(String name, String tagName, String tagValue) {
        Counter.builder(name)
                .tag(tagName, normalize(tagValue))
                .register(meterRegistry)
                .increment();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

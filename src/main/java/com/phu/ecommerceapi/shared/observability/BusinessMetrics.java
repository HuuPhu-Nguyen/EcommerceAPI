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
        paymentOutcome("unknown", status);
    }

    public void paymentOutcome(String provider, String status) {
        Counter.builder("app.payment.outcomes")
                .tag("provider", normalize(provider))
                .tag("status", normalize(status))
                .register(meterRegistry)
                .increment();
    }

    public void refundOutcome(String status) {
        refundOutcome("unknown", status);
    }

    public void refundOutcome(String provider, String status) {
        Counter.builder("app.refund.outcomes")
                .tag("provider", normalize(provider))
                .tag("status", normalize(status))
                .register(meterRegistry)
                .increment();
    }

    public void providerWebhook(String provider, String status) {
        Counter.builder("app.provider_webhook.processed")
                .tag("provider", normalize(provider))
                .tag("status", normalize(status))
                .register(meterRegistry)
                .increment();
    }

    public void stripeProviderOperation(String operation, String outcome) {
        Counter.builder("app.payment_provider.operations")
                .tag("provider", "stripe")
                .tag("operation", normalize(operation))
                .tag("outcome", normalize(outcome))
                .register(meterRegistry)
                .increment();
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

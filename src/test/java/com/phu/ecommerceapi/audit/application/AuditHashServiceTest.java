package com.phu.ecommerceapi.audit.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashServiceTest {

    private final AuditHashService auditHashService = new AuditHashService();

    @Test
    void sameCanonicalPayloadProducesSameHash() {
        AuditHashPayload payload = payload("amount=20.00 USD");

        String firstHash = auditHashService.hash(payload);
        String secondHash = auditHashService.hash(payload);

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).hasSize(64);
    }

    @Test
    void changedPayloadProducesDifferentHash() {
        String originalHash = auditHashService.hash(payload("amount=20.00 USD"));
        String changedHash = auditHashService.hash(payload("amount=999.00 USD"));

        assertThat(changedHash).isNotEqualTo(originalHash);
    }

    private AuditHashPayload payload(String details) {
        return new AuditHashPayload(
                null,
                "actor-subject",
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                "payment-1",
                details,
                "request-1",
                "203.0.113.10",
                "AuditHashServiceTest/1.0",
                Instant.parse("2026-07-06T00:00:00Z")
        );
    }
}

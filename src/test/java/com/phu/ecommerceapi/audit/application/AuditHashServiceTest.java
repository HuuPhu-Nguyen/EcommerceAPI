package com.phu.ecommerceapi.audit.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashServiceTest {

    private static final String SIGNATURE_SECRET = "test-audit-signature-secret";

    private final AuditHashService auditHashService = new AuditHashService(SIGNATURE_SECRET);

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

    @Test
    void sameEventHashProducesSameSignature() {
        String eventHash = auditHashService.hash(payload("amount=20.00 USD"));

        String firstSignature = auditHashService.sign(eventHash);
        String secondSignature = auditHashService.sign(eventHash);

        assertThat(firstSignature).isEqualTo(secondSignature);
        assertThat(firstSignature).hasSize(64);
        assertThat(firstSignature).isNotEqualTo(eventHash);
    }

    @Test
    void signatureValidationRequiresSameSecretAndHash() {
        String eventHash = auditHashService.hash(payload("amount=20.00 USD"));
        String signature = auditHashService.sign(eventHash);
        AuditHashService otherSecretService = new AuditHashService("different-secret");

        assertThat(auditHashService.signatureMatches(eventHash, signature)).isTrue();
        assertThat(auditHashService.signatureMatches("0" + eventHash.substring(1), signature)).isFalse();
        assertThat(otherSecretService.signatureMatches(eventHash, signature)).isFalse();
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

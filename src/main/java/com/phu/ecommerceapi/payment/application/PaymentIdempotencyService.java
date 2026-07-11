package com.phu.ecommerceapi.payment.application;

import com.phu.ecommerceapi.shared.api.ConflictException;
import com.phu.ecommerceapi.shared.observability.BusinessMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentIdempotencyService {

    private final PaymentIdempotencyPersistencePort persistencePort;
    private final BusinessMetrics businessMetrics;
    private final long inProgressLeaseSeconds;

    public PaymentIdempotencyService(
            PaymentIdempotencyPersistencePort persistencePort,
            BusinessMetrics businessMetrics,
            @Value("${app.payment.idempotency.in-progress-lease-seconds:300}") long inProgressLeaseSeconds
    ) {
        this.persistencePort = persistencePort;
        this.businessMetrics = businessMetrics;
        this.inProgressLeaseSeconds = Math.max(1, inProgressLeaseSeconds);
    }

    @Transactional
    public PaymentIdempotencyDecision start(PaymentIdempotencyCommand command) {
        String requestHash = hash(command.requestBody());
        OffsetDateTime now = OffsetDateTime.now();
        PaymentIdempotencyReservation reservation = persistencePort.reserve(
                command,
                requestHash,
                now,
                now.plusSeconds(inProgressLeaseSeconds)
        );
        PaymentIdempotencyEntry entry = reservation.entry();

        if (reservation.started()) {
            PaymentIdempotencyDecision decision = PaymentIdempotencyDecision.started(entry.recordId());
            recordDecision(decision.type());
            return decision;
        }
        return existingDecision(entry, requestHash);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentIdempotencyDecision> findExisting(PaymentIdempotencyCommand command) {
        String requestHash = hash(command.requestBody());
        return persistencePort.find(command, requestHash)
                .map(entry -> existingDecision(entry, requestHash));
    }

    @Transactional
    public void complete(long recordId, int responseStatus, String responseBody) {
        persistencePort.complete(recordId, responseStatus, responseBody);
    }

    @Transactional
    public void linkPaymentAttempt(
            long recordId,
            UUID paymentId,
            String providerCode,
            String providerIdempotencyKey
    ) {
        persistencePort.linkResource(recordId, "PAYMENT", paymentId, providerCode, providerIdempotencyKey);
    }

    @Transactional
    public void linkRefundAttempt(
            long recordId,
            UUID refundId,
            String providerCode,
            String providerIdempotencyKey
    ) {
        persistencePort.linkResource(recordId, "REFUND", refundId, providerCode, providerIdempotencyKey);
    }

    private PaymentIdempotencyDecision existingDecision(PaymentIdempotencyEntry entry, String requestHash) {
        if (!entry.hasRequestHash(requestHash)) {
            businessMetrics.idempotencyDecision("CONFLICT");
            throw new ConflictException("Idempotency key was reused with a different request body");
        }
        if (entry.completed()) {
            PaymentIdempotencyDecision decision = PaymentIdempotencyDecision.replay(
                    entry.recordId(),
                    entry.responseStatus(),
                    entry.responseBody()
            );
            recordDecision(decision.type());
            return decision;
        }
        PaymentIdempotencyDecision decision = PaymentIdempotencyDecision.inProgress(
                entry.recordId(),
                entry.resourceType(),
                entry.resourceId(),
                entry.providerCode(),
                entry.providerIdempotencyKey()
        );
        recordDecision(decision.type());
        return decision;
    }

    private void recordDecision(PaymentIdempotencyDecisionType decisionType) {
        businessMetrics.idempotencyDecision(decisionType.name());
    }

    private String hash(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

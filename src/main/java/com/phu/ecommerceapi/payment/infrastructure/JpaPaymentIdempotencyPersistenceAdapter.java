package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentIdempotencyCommand;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyEntry;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyPersistencePort;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyRecoveryEntry;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyRecoveryPort;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyReservation;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaPaymentIdempotencyPersistenceAdapter
        implements PaymentIdempotencyPersistencePort, PaymentIdempotencyRecoveryPort {

    private final PaymentIdempotencyRecordRepository repository;

    public JpaPaymentIdempotencyPersistenceAdapter(PaymentIdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaymentIdempotencyReservation reserve(
            PaymentIdempotencyCommand command,
            String requestHash,
            OffsetDateTime createdAt,
            OffsetDateTime inProgressExpiresAt
    ) {
        int insertedRows = repository.insertInProgress(
                command.customerId(),
                command.endpoint(),
                command.operation(),
                command.idempotencyKey(),
                requestHash,
                inProgressExpiresAt,
                createdAt
        );

        PaymentIdempotencyEntry entry = find(command, requestHash)
                .orElseThrow(() -> new IllegalStateException("Idempotency record was not persisted"));
        return new PaymentIdempotencyReservation(entry, insertedRows == 1);
    }

    @Override
    public Optional<PaymentIdempotencyEntry> find(PaymentIdempotencyCommand command, String requestHash) {
        return repository
                .findByCustomerIdAndEndpointAndOperationAndIdempotencyKey(
                        command.customerId(),
                        command.endpoint(),
                        command.operation(),
                        command.idempotencyKey()
                )
                .map(this::toEntry);
    }

    @Override
    public void linkResource(
            long recordId,
            String resourceType,
            UUID resourceId,
            String providerCode,
            String providerIdempotencyKey
    ) {
        PaymentIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency record not found"));
        record.linkResource(resourceType, resourceId, providerCode, providerIdempotencyKey);
    }

    @Override
    public void complete(long recordId, int responseStatus, String responseBody) {
        PaymentIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency record not found"));
        record.complete(responseStatus, responseBody);
    }

    @Override
    public List<PaymentIdempotencyRecoveryEntry> claimExpired(OffsetDateTime now, int limit) {
        return repository.findExpiredForRecovery(now, limit).stream()
                .peek(record -> record.markRecoveryAttempt(now))
                .map(this::toRecoveryEntry)
                .toList();
    }

    @Override
    public void completeRecovered(long recordId, int responseStatus, String responseBody, OffsetDateTime recoveredAt) {
        PaymentIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency record not found"));
        record.completeRecovered(responseStatus, responseBody, recoveredAt);
    }

    @Override
    public void markPendingReconciliation(long recordId, OffsetDateTime attemptedAt) {
        PaymentIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency record not found"));
        record.markPendingReconciliation(attemptedAt);
    }

    @Override
    public void markManualReview(long recordId, OffsetDateTime attemptedAt) {
        PaymentIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency record not found"));
        record.markManualReview(attemptedAt);
    }

    private PaymentIdempotencyEntry toEntry(PaymentIdempotencyRecord record) {
        return new PaymentIdempotencyEntry(
                record.getId(),
                record.getRequestHash(),
                record.isCompleted(),
                record.getResponseStatus(),
                record.getResponseBody(),
                record.getResourceType(),
                record.getResourceId(),
                record.getProviderCode(),
                record.getProviderIdempotencyKey()
        );
    }

    private PaymentIdempotencyRecoveryEntry toRecoveryEntry(PaymentIdempotencyRecord record) {
        return new PaymentIdempotencyRecoveryEntry(
                record.getId(),
                record.getResourceType(),
                record.getResourceId(),
                record.getProviderCode(),
                record.getProviderIdempotencyKey()
        );
    }
}

package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.PaymentIdempotencyCommand;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyEntry;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyPersistencePort;
import com.phu.ecommerceapi.payment.application.PaymentIdempotencyReservation;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaPaymentIdempotencyPersistenceAdapter implements PaymentIdempotencyPersistencePort {

    private final PaymentIdempotencyRecordRepository repository;

    public JpaPaymentIdempotencyPersistenceAdapter(PaymentIdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaymentIdempotencyReservation reserve(
            PaymentIdempotencyCommand command,
            String requestHash,
            OffsetDateTime createdAt
    ) {
        int insertedRows = repository.insertInProgress(
                command.customerId(),
                command.endpoint(),
                command.operation(),
                command.idempotencyKey(),
                requestHash,
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
}

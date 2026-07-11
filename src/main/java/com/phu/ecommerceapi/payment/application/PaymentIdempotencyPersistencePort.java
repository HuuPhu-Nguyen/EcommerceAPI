package com.phu.ecommerceapi.payment.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIdempotencyPersistencePort {

    PaymentIdempotencyReservation reserve(
            PaymentIdempotencyCommand command,
            String requestHash,
            OffsetDateTime createdAt,
            OffsetDateTime inProgressExpiresAt
    );

    Optional<PaymentIdempotencyEntry> find(PaymentIdempotencyCommand command, String requestHash);

    void linkResource(
            long recordId,
            String resourceType,
            UUID resourceId,
            String providerCode,
            String providerIdempotencyKey
    );

    void complete(long recordId, int responseStatus, String responseBody);
}

package com.phu.ecommerceapi.payment.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PaymentIdempotencyPersistencePort {

    PaymentIdempotencyReservation reserve(
            PaymentIdempotencyCommand command,
            String requestHash,
            OffsetDateTime createdAt
    );

    Optional<PaymentIdempotencyEntry> find(PaymentIdempotencyCommand command, String requestHash);

    void complete(long recordId, int responseStatus, String responseBody);
}

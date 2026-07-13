package com.phu.ecommerceapi.outbox.application;

import java.time.Instant;
import java.util.Objects;

public record ClaimedOutboxEvent(
        OutboxEvent event,
        Instant claimedAt,
        int attempts
) {

    public ClaimedOutboxEvent {
        Objects.requireNonNull(event, "claimed outbox event is required");
        Objects.requireNonNull(claimedAt, "claimed at is required");
        if (attempts < 0) {
            throw new IllegalArgumentException("outbox event attempts cannot be negative");
        }
    }
}

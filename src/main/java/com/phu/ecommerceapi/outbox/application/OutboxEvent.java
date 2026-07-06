package com.phu.ecommerceapi.outbox.application;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant createdAt
) {
}

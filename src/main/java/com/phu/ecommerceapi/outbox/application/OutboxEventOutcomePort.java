package com.phu.ecommerceapi.outbox.application;

import java.time.Instant;
import java.util.UUID;

public interface OutboxEventOutcomePort {

    void markProcessed(UUID eventId, Instant claimedAt, Instant processedAt);

    void markFailed(UUID eventId, Instant claimedAt, String error, Instant nextAttemptAt);
}

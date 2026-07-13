package com.phu.ecommerceapi.outbox.application;

import java.time.Instant;
import java.util.List;

public interface OutboxEventClaimPort {

    List<ClaimedOutboxEvent> claimDueEvents(
            Instant claimedAt,
            int batchSize,
            Instant timedOutBefore,
            String timeoutError
    );
}

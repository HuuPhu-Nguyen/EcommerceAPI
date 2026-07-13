package com.phu.ecommerceapi.outbox.application;

import java.time.Instant;
import java.util.Optional;

public interface OutboxEventMonitoringPort {

    long countPending();

    long countFailed();

    Optional<Instant> oldestUnprocessedCreatedAt();
}

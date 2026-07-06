package com.phu.ecommerceapi.outbox.application;

import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRepository;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxMetrics {

    private static final List<OutboxEventStatus> UNPROCESSED_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.FAILED
    );

    public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        Gauge.builder(
                        "app.outbox.pending.count",
                        outboxEventRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.PENDING)
                )
                .description("Number of outbox events waiting for first delivery attempt")
                .register(meterRegistry);
        Gauge.builder(
                        "app.outbox.failed.count",
                        outboxEventRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.FAILED)
                )
                .description("Number of outbox events waiting for retry after a failed delivery attempt")
                .register(meterRegistry);
        Gauge.builder(
                        "app.outbox.lag.seconds",
                        outboxEventRepository,
                        this::lagSeconds
                )
                .description("Age in seconds of the oldest pending or failed outbox event")
                .register(meterRegistry);
    }

    private double lagSeconds(OutboxEventRepository repository) {
        return repository.oldestCreatedAtForStatuses(UNPROCESSED_STATUSES)
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).toSeconds()))
                .orElse(0L);
    }
}

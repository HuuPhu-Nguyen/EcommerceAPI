package com.phu.ecommerceapi.outbox.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventMonitoringPort outboxEventMonitoringPort) {
        Gauge.builder(
                        "app.outbox.pending.count",
                        outboxEventMonitoringPort,
                        OutboxEventMonitoringPort::countPending
                )
                .description("Number of outbox events waiting for first delivery attempt")
                .register(meterRegistry);
        Gauge.builder(
                        "app.outbox.failed.count",
                        outboxEventMonitoringPort,
                        OutboxEventMonitoringPort::countFailed
                )
                .description("Number of outbox events waiting for retry after a failed delivery attempt")
                .register(meterRegistry);
        Gauge.builder(
                        "app.outbox.lag.seconds",
                        outboxEventMonitoringPort,
                        this::lagSeconds
                )
                .description("Age in seconds of the oldest pending or failed outbox event")
                .register(meterRegistry);
    }

    private double lagSeconds(OutboxEventMonitoringPort monitoringPort) {
        return monitoringPort.oldestUnprocessedCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).toSeconds()))
                .orElse(0L);
    }
}

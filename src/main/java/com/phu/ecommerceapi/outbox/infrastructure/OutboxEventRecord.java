package com.phu.ecommerceapi.outbox.infrastructure;

import com.phu.ecommerceapi.outbox.application.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEventRecord {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false, length = 150)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    private Instant lockedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OutboxEventRecord() {
    }

    private OutboxEventRecord(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "outbox event id is required");
        this.aggregateType = requireText(aggregateType, "aggregate type is required");
        this.aggregateId = requireText(aggregateId, "aggregate id is required");
        this.eventType = requireText(eventType, "event type is required");
        this.payload = requireText(payload, "payload is required");
        this.status = OutboxEventStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = Objects.requireNonNull(createdAt, "created at is required");
        this.createdAt = createdAt;
    }

    public static OutboxEventRecord pending(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        return new OutboxEventRecord(id, aggregateType, aggregateId, eventType, payload, createdAt);
    }

    public void markProcessing(Instant lockedAt) {
        this.status = OutboxEventStatus.PROCESSING;
        this.lockedAt = Objects.requireNonNull(lockedAt, "locked at is required");
    }

    public void markProcessed(Instant processedAt) {
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = Objects.requireNonNull(processedAt, "processed at is required");
        this.lockedAt = null;
        this.lastError = null;
    }

    public void markFailed(String error, Instant nextAttemptAt) {
        this.status = OutboxEventStatus.FAILED;
        this.attempts++;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "next attempt at is required");
        this.lockedAt = null;
        this.lastError = truncate(requireText(error, "error is required"));
    }

    public OutboxEvent toEvent() {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, createdAt);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }
}

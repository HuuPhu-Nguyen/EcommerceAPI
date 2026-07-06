package com.phu.ecommerceapi.outbox.application;

import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRecord;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OutboxEventProcessor {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_BACKOFF_SECONDS = 300;

    private final OutboxEventRepository outboxEventRepository;
    private final List<OutboxEventPublisher> publishers;

    public OutboxEventProcessor(
            OutboxEventRepository outboxEventRepository,
            List<OutboxEventPublisher> publishers
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.publishers = List.copyOf(publishers);
    }

    @Transactional
    public int processPendingBatch(int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        Instant now = now();
        List<OutboxEventRecord> events = outboxEventRepository.findDueForProcessing(now, safeBatchSize);
        for (OutboxEventRecord event : events) {
            process(event, now);
        }
        return events.size();
    }

    private void process(OutboxEventRecord record, Instant batchStartedAt) {
        record.markProcessing(batchStartedAt);
        try {
            OutboxEvent event = record.toEvent();
            List<OutboxEventPublisher> matchingPublishers = publishers.stream()
                    .filter(publisher -> publisher.supports(event))
                    .toList();
            if (matchingPublishers.isEmpty()) {
                throw new IllegalStateException("No outbox publisher registered for event type " + record.getEventType());
            }
            matchingPublishers.forEach(publisher -> publisher.publish(event));
            record.markProcessed(now());
        } catch (RuntimeException exception) {
            record.markFailed(errorMessage(exception), nextAttemptAt(record.getAttempts(), batchStartedAt));
        }
    }

    private Instant nextAttemptAt(int attemptsBeforeFailure, Instant failedAt) {
        int backoffSeconds = Math.min(MAX_BACKOFF_SECONDS, 1 << Math.min(attemptsBeforeFailure, 8));
        return failedAt.plusSeconds(backoffSeconds);
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}

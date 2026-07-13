package com.phu.ecommerceapi.outbox.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OutboxEventProcessor {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_BACKOFF_SECONDS = 300;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final String PROCESSING_TIMEOUT_ERROR = "Outbox event processing timed out before completion";

    private final OutboxEventClaimPort outboxEventClaimPort;
    private final OutboxEventOutcomePort outboxEventOutcomePort;
    private final List<OutboxEventPublisher> publishers;
    private final TransactionTemplate transactionTemplate;

    public OutboxEventProcessor(
            OutboxEventClaimPort outboxEventClaimPort,
            OutboxEventOutcomePort outboxEventOutcomePort,
            List<OutboxEventPublisher> publishers,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxEventClaimPort = outboxEventClaimPort;
        this.outboxEventOutcomePort = outboxEventOutcomePort;
        this.publishers = List.copyOf(publishers);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int processPendingBatch(int batchSize) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox processing must run outside an active transaction");
        }
        int safeBatchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<ClaimedOutboxEvent> events = claimDueEvents(safeBatchSize);
        for (ClaimedOutboxEvent event : events) {
            publishAndMarkOutcome(event);
        }
        return events.size();
    }

    private List<ClaimedOutboxEvent> claimDueEvents(int batchSize) {
        Instant claimedAt = now();
        return transactionTemplate.execute(status -> outboxEventClaimPort.claimDueEvents(
                claimedAt,
                batchSize,
                claimedAt.minus(PROCESSING_TIMEOUT),
                PROCESSING_TIMEOUT_ERROR
        ));
    }

    private void publishAndMarkOutcome(ClaimedOutboxEvent claimedEvent) {
        try {
            publish(claimedEvent.event());
            markProcessed(claimedEvent);
        } catch (RuntimeException exception) {
            markFailed(claimedEvent, exception);
        }
    }

    private void publish(OutboxEvent event) {
        List<OutboxEventPublisher> matchingPublishers = publishers.stream()
                .filter(publisher -> publisher.supports(event))
                .toList();
        if (matchingPublishers.isEmpty()) {
            throw new IllegalStateException("No outbox publisher registered for event type " + event.eventType());
        }
        matchingPublishers.forEach(publisher -> publisher.publish(event));
    }

    private void markProcessed(ClaimedOutboxEvent claimedEvent) {
        transactionTemplate.executeWithoutResult(status -> outboxEventOutcomePort.markProcessed(
                claimedEvent.event().id(),
                claimedEvent.claimedAt(),
                now()
        ));
    }

    private void markFailed(ClaimedOutboxEvent claimedEvent, RuntimeException exception) {
        Instant failedAt = now();
        transactionTemplate.executeWithoutResult(status -> outboxEventOutcomePort.markFailed(
                claimedEvent.event().id(),
                claimedEvent.claimedAt(),
                errorMessage(exception),
                nextAttemptAt(claimedEvent.attempts(), failedAt)
        ));
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

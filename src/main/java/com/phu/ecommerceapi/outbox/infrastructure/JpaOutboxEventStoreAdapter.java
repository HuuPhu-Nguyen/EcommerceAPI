package com.phu.ecommerceapi.outbox.infrastructure;

import com.phu.ecommerceapi.outbox.application.ClaimedOutboxEvent;
import com.phu.ecommerceapi.outbox.application.OutboxEvent;
import com.phu.ecommerceapi.outbox.application.OutboxEventClaimPort;
import com.phu.ecommerceapi.outbox.application.OutboxEventMonitoringPort;
import com.phu.ecommerceapi.outbox.application.OutboxEventOutcomePort;
import com.phu.ecommerceapi.outbox.application.OutboxEventStorePort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaOutboxEventStoreAdapter implements
        OutboxEventStorePort,
        OutboxEventClaimPort,
        OutboxEventOutcomePort,
        OutboxEventMonitoringPort {

    private static final List<OutboxEventStatus> UNPROCESSED_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.FAILED
    );

    private final OutboxEventRepository outboxEventRepository;

    public JpaOutboxEventStoreAdapter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void savePending(OutboxEvent event) {
        outboxEventRepository.save(OutboxEventRecord.pending(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.createdAt()
        ));
    }

    @Override
    public List<ClaimedOutboxEvent> claimDueEvents(
            Instant claimedAt,
            int batchSize,
            Instant timedOutBefore,
            String timeoutError
    ) {
        outboxEventRepository.resetTimedOutProcessing(timedOutBefore, claimedAt, timeoutError);
        List<OutboxEventRecord> records = outboxEventRepository.findDueForProcessing(claimedAt, batchSize);
        for (OutboxEventRecord record : records) {
            record.markProcessing(claimedAt);
        }
        return records.stream()
                .map(record -> new ClaimedOutboxEvent(record.toEvent(), claimedAt, record.getAttempts()))
                .toList();
    }

    @Override
    public void markProcessed(UUID eventId, Instant claimedAt, Instant processedAt) {
        outboxEventRepository.findByIdForUpdate(eventId)
                .ifPresent(record -> {
                    if (record.isProcessed() || !record.isProcessingClaim(claimedAt)) {
                        return;
                    }
                    record.markProcessed(processedAt);
                });
    }

    @Override
    public void markFailed(UUID eventId, Instant claimedAt, String error, Instant nextAttemptAt) {
        outboxEventRepository.findByIdForUpdate(eventId)
                .ifPresent(record -> {
                    if (record.isProcessed() || !record.isProcessingClaim(claimedAt)) {
                        return;
                    }
                    record.markFailed(error, nextAttemptAt);
                });
    }

    @Override
    public long countPending() {
        return outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
    }

    @Override
    public long countFailed() {
        return outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
    }

    @Override
    public Optional<Instant> oldestUnprocessedCreatedAt() {
        return outboxEventRepository.oldestCreatedAtForStatuses(UNPROCESSED_STATUSES);
    }
}

package com.phu.ecommerceapi.outbox.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.processing-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventProcessingScheduler {

    private final OutboxEventProcessor outboxEventProcessor;
    private final int batchSize;

    public OutboxEventProcessingScheduler(
            OutboxEventProcessor outboxEventProcessor,
            @Value("${app.outbox.batch-size:50}") int batchSize
    ) {
        this.outboxEventProcessor = outboxEventProcessor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.processing-fixed-delay-ms:2000}")
    public void processPendingEvents() {
        outboxEventProcessor.processPendingBatch(batchSize);
    }
}

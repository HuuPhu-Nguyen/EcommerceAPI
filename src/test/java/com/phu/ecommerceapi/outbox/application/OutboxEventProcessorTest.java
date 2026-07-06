package com.phu.ecommerceapi.outbox.application;

import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRecord;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventRepository;
import com.phu.ecommerceapi.outbox.infrastructure.OutboxEventStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventProcessorTest {

    @Autowired
    private OutboxEventRecorder outboxEventRecorder;

    @Autowired
    private OutboxEventProcessor outboxEventProcessor;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RecordingOutboxEventPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void resetOutbox() {
        outboxEventRepository.deleteAll();
        publisher.reset();
    }

    @Test
    void recorderRequiresExistingTransaction() {
        assertThatThrownBy(() -> outboxEventRecorder.record("PRODUCT", "1", "TestEvent", Map.of("value", "outside")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void processorPublishesDueEventsAndMarksProcessed() {
        UUID eventId = recordTestEvent("delivered");

        int processed = outboxEventProcessor.processPendingBatch(10);

        OutboxEventRecord event = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0).id()).isEqualTo(eventId);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void processorMarksFailedEventsForRetry() {
        UUID eventId = recordTestEvent("fail-once");
        publisher.failNextPublish();

        int processed = outboxEventProcessor.processPendingBatch(10);

        OutboxEventRecord event = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(publisher.publishedEvents()).isEmpty();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("publisher failed");
        assertThat(event.getNextAttemptAt()).isAfter(event.getCreatedAt());
    }

    @Test
    void outboxMetricsExposePendingFailedCountsAndLag() {
        recordTestEvent("metrics");

        assertThat(gauge("app.outbox.pending.count")).isEqualTo(1.0);
        assertThat(gauge("app.outbox.failed.count")).isZero();
        assertThat(gauge("app.outbox.lag.seconds")).isGreaterThanOrEqualTo(0.0);

        publisher.failNextPublish();
        outboxEventProcessor.processPendingBatch(10);

        assertThat(gauge("app.outbox.pending.count")).isZero();
        assertThat(gauge("app.outbox.failed.count")).isEqualTo(1.0);
    }

    private UUID recordTestEvent(String value) {
        return transactionTemplate.execute(status -> outboxEventRecorder.record(
                "PRODUCT",
                "1",
                "TestEvent",
                Map.of("value", value)
        ));
    }

    private double gauge(String name) {
        return meterRegistry.find(name).gauge().value();
    }

    @TestConfiguration
    static class PublisherConfig {

        @Bean
        RecordingOutboxEventPublisher recordingOutboxEventPublisher() {
            return new RecordingOutboxEventPublisher();
        }
    }

    static class RecordingOutboxEventPublisher implements OutboxEventPublisher {

        private final List<OutboxEvent> publishedEvents = new ArrayList<>();
        private boolean failNextPublish;

        @Override
        public boolean supports(OutboxEvent event) {
            return "TestEvent".equals(event.eventType());
        }

        @Override
        public void publish(OutboxEvent event) {
            if (failNextPublish) {
                failNextPublish = false;
                throw new IllegalStateException("publisher failed");
            }
            publishedEvents.add(event);
        }

        void failNextPublish() {
            failNextPublish = true;
        }

        List<OutboxEvent> publishedEvents() {
            return publishedEvents;
        }

        void reset() {
            publishedEvents.clear();
            failNextPublish = false;
        }
    }
}

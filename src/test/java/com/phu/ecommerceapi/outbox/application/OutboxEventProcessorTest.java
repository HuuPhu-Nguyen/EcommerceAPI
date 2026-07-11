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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void processorPublishesOutsideTheClaimTransaction() {
        recordTestEvent("outside-transaction");

        outboxEventProcessor.processPendingBatch(10);

        assertThat(publisher.transactionActiveDuringPublishes()).containsExactly(false);
    }

    @Test
    void processorRejectsCallsInsideAnActiveTransaction() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> outboxEventProcessor.processPendingBatch(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbox processing must run outside an active transaction");
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
    void failedPublishIsRetriedWhenNextAttemptIsDue() {
        UUID eventId = recordTestEvent("retry-after-failure");
        publisher.failNextPublish();
        outboxEventProcessor.processPendingBatch(10);

        jdbcTemplate.update(
                "UPDATE outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                eventId
        );

        int processed = outboxEventProcessor.processPendingBatch(10);

        OutboxEventRecord event = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(publisher.publishedEvents())
                .singleElement()
                .satisfies(publishedEvent -> assertThat(publishedEvent.id()).isEqualTo(eventId));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void abandonedProcessingEventIsReturnedForRetry() {
        UUID eventId = recordTestEvent("abandoned-processing");
        transactionTemplate.executeWithoutResult(status -> outboxEventRepository
                .findById(eventId)
                .orElseThrow()
                .markProcessing(Instant.now().minus(Duration.ofMinutes(10))));

        int processed = outboxEventProcessor.processPendingBatch(10);

        OutboxEventRecord event = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(publisher.publishedEvents())
                .singleElement()
                .satisfies(publishedEvent -> assertThat(publishedEvent.id()).isEqualTo(eventId));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void concurrentProcessorsDoNotPublishSameClaimedEvent() throws Exception {
        UUID eventId = recordTestEvent("concurrent-claim");
        publisher.blockPublishes();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> firstProcessor = executor.submit(() -> outboxEventProcessor.processPendingBatch(10));
            assertThat(publisher.awaitPublishStarted()).isTrue();

            Future<Integer> secondProcessor = executor.submit(() -> outboxEventProcessor.processPendingBatch(10));
            assertThat(secondProcessor.get(5, TimeUnit.SECONDS)).isZero();

            publisher.releasePublishes();
            assertThat(firstProcessor.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            publisher.releasePublishes();
            executor.shutdownNow();
        }

        assertThat(publisher.publishedEvents())
                .singleElement()
                .satisfies(publishedEvent -> assertThat(publishedEvent.id()).isEqualTo(eventId));
        assertThat(outboxEventRepository.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PROCESSED);
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

        private final List<OutboxEvent> publishedEvents = new CopyOnWriteArrayList<>();
        private final List<Boolean> transactionActiveDuringPublishes = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch publishStarted = new CountDownLatch(0);
        private volatile CountDownLatch releasePublish = new CountDownLatch(0);
        private volatile boolean blockPublishes;
        private volatile boolean failNextPublish;

        @Override
        public boolean supports(OutboxEvent event) {
            return "TestEvent".equals(event.eventType());
        }

        @Override
        public void publish(OutboxEvent event) {
            transactionActiveDuringPublishes.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failNextPublish) {
                failNextPublish = false;
                throw new IllegalStateException("publisher failed");
            }
            if (blockPublishes) {
                publishStarted.countDown();
                awaitRelease();
            }
            publishedEvents.add(event);
        }

        void failNextPublish() {
            failNextPublish = true;
        }

        List<OutboxEvent> publishedEvents() {
            return publishedEvents;
        }

        List<Boolean> transactionActiveDuringPublishes() {
            return transactionActiveDuringPublishes;
        }

        void blockPublishes() {
            blockPublishes = true;
            publishStarted = new CountDownLatch(1);
            releasePublish = new CountDownLatch(1);
        }

        boolean awaitPublishStarted() throws InterruptedException {
            return publishStarted.await(5, TimeUnit.SECONDS);
        }

        void releasePublishes() {
            releasePublish.countDown();
            blockPublishes = false;
        }

        void reset() {
            publishedEvents.clear();
            transactionActiveDuringPublishes.clear();
            releasePublishes();
            publishStarted = new CountDownLatch(0);
            releasePublish = new CountDownLatch(0);
            failNextPublish = false;
        }

        private void awaitRelease() {
            try {
                if (!releasePublish.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("publisher release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("publisher interrupted", exception);
            }
        }
    }
}

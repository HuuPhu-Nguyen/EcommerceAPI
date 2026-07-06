package com.phu.ecommerceapi.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.outbox.application.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockChangedOutboxPublisherTest {

    private final RecordingStockEventPublisher stockEventPublisher = new RecordingStockEventPublisher();
    private final StockChangedOutboxPublisher publisher = new StockChangedOutboxPublisher(
            new ObjectMapper(),
            stockEventPublisher
    );

    @Test
    void supportsOnlyStockChangedEvents() {
        assertThat(publisher.supports(event("StockChanged", payload()))).isTrue();
        assertThat(publisher.supports(event("OtherEvent", payload()))).isFalse();
    }

    @Test
    void publishesParsedStockChangedPayload() {
        Instant occurredAt = Instant.parse("2026-07-06T08:00:00Z");

        publisher.publish(new OutboxEvent(
                UUID.randomUUID(),
                "PRODUCT",
                "10",
                "StockChanged",
                payload(),
                occurredAt
        ));

        assertThat(stockEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.productId()).isEqualTo(10L);
                    assertThat(event.availableQuantity()).isEqualTo(4);
                    assertThat(event.reservedQuantity()).isEqualTo(1);
                    assertThat(event.reason()).isEqualTo("RESERVED");
                    assertThat(event.occurredAt()).isEqualTo(occurredAt);
                    assertThat(event.advisory()).isTrue();
                });
    }

    @Test
    void invalidPayloadFailsProcessing() {
        assertThatThrownBy(() -> publisher.publish(event("StockChanged", "{")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("StockChanged outbox payload could not be parsed");
    }

    private OutboxEvent event(String eventType, String payload) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "PRODUCT",
                "10",
                eventType,
                payload,
                Instant.parse("2026-07-06T08:00:00Z")
        );
    }

    private String payload() {
        return """
                {
                  "productId": 10,
                  "availableQuantity": 4,
                  "reservedQuantity": 1,
                  "reason": "RESERVED"
                }
                """;
    }

    private static class RecordingStockEventPublisher implements StockEventPublisher {

        private final List<StockChangedSseEvent> events = new ArrayList<>();

        @Override
        public int publish(StockChangedSseEvent event) {
            events.add(event);
            return 1;
        }
    }
}

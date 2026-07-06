package com.phu.ecommerceapi.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phu.ecommerceapi.outbox.application.OutboxEvent;
import com.phu.ecommerceapi.outbox.application.OutboxEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class StockChangedOutboxPublisher implements OutboxEventPublisher {

    private static final String STOCK_CHANGED_EVENT_TYPE = "StockChanged";

    private final ObjectMapper objectMapper;
    private final StockEventPublisher stockEventPublisher;

    public StockChangedOutboxPublisher(
            ObjectMapper objectMapper,
            StockEventPublisher stockEventPublisher
    ) {
        this.objectMapper = objectMapper;
        this.stockEventPublisher = stockEventPublisher;
    }

    @Override
    public boolean supports(OutboxEvent event) {
        return STOCK_CHANGED_EVENT_TYPE.equals(event.eventType());
    }

    @Override
    public void publish(OutboxEvent event) {
        StockChangedEventPayload payload = payload(event);
        stockEventPublisher.publish(new StockChangedSseEvent(
                payload.productId(),
                payload.availableQuantity(),
                payload.reservedQuantity(),
                payload.reason(),
                event.createdAt(),
                true
        ));
    }

    private StockChangedEventPayload payload(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.payload(), StockChangedEventPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("StockChanged outbox payload could not be parsed", exception);
        }
    }
}

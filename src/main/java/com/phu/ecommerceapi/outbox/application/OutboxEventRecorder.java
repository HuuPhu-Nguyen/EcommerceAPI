package com.phu.ecommerceapi.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class OutboxEventRecorder {

    private final OutboxEventStorePort outboxEventStorePort;
    private final ObjectMapper objectMapper;

    public OutboxEventRecorder(
            OutboxEventStorePort outboxEventStorePort,
            ObjectMapper objectMapper
    ) {
        this.outboxEventStorePort = outboxEventStorePort;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(String aggregateType, String aggregateId, String eventType, Object payload) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                serialize(payload),
                now
        );
        outboxEventStorePort.savePending(event);
        return event.id();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox event payload could not be serialized", exception);
        }
    }
}

package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.application.ProviderWebhookEventView;
import com.phu.ecommerceapi.payment.application.ProviderWebhookPersistencePort;
import com.phu.ecommerceapi.payment.application.ProviderWebhookRegistration;
import com.phu.ecommerceapi.payment.application.ProviderWebhookRegistrationCommand;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaProviderWebhookPersistenceAdapter implements ProviderWebhookPersistencePort {

    private final ProviderWebhookEventRepository eventRepository;

    public JpaProviderWebhookPersistenceAdapter(ProviderWebhookEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public ProviderWebhookRegistration registerReceived(ProviderWebhookRegistrationCommand command) {
        UUID eventId = UUID.randomUUID();
        int insertedRows = eventRepository.insertReceived(
                eventId,
                command.providerName(),
                command.providerEventId(),
                command.eventType().name(),
                command.payloadHash(),
                command.payload(),
                command.receivedAt()
        );

        ProviderWebhookEventRecord event = eventRepository
                .findByProviderNameAndProviderEventId(command.providerName(), command.providerEventId())
                .orElseThrow(() -> new IllegalStateException("Provider webhook event was not persisted"));
        return new ProviderWebhookRegistration(
                toView(event),
                insertedRows == 1,
                event.hasPayloadHash(command.payloadHash())
        );
    }

    @Override
    public ProviderWebhookEventView markProcessed(UUID eventId, String message) {
        ProviderWebhookEventRecord event = event(eventId);
        event.markProcessed(message);
        return toView(event);
    }

    @Override
    public ProviderWebhookEventView markIgnored(UUID eventId, String message) {
        ProviderWebhookEventRecord event = event(eventId);
        event.markIgnored(message);
        return toView(event);
    }

    @Override
    public ProviderWebhookEventView markRejected(UUID eventId, String message) {
        ProviderWebhookEventRecord event = event(eventId);
        event.markRejected(message);
        return toView(event);
    }

    private ProviderWebhookEventRecord event(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Provider webhook event not found"));
    }

    private ProviderWebhookEventView toView(ProviderWebhookEventRecord event) {
        return new ProviderWebhookEventView(
                event.getId(),
                event.getProviderEventId(),
                event.getProcessingStatus(),
                event.getProcessingMessage()
        );
    }
}

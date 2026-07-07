package com.phu.ecommerceapi.payment.application;

import java.util.UUID;

public interface ProviderWebhookPersistencePort {

    ProviderWebhookRegistration registerReceived(ProviderWebhookRegistrationCommand command);

    ProviderWebhookEventView markProcessed(UUID eventId, String message);

    ProviderWebhookEventView markIgnored(UUID eventId, String message);

    ProviderWebhookEventView markRejected(UUID eventId, String message);
}

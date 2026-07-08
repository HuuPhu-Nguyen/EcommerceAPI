package com.phu.ecommerceapi.payment.application;

public interface ProviderWebhookHandler {

    ProviderWebhookResult handle(ProviderWebhookCommand command);
}

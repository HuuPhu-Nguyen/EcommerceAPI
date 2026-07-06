package com.phu.ecommerceapi.outbox.application;

public interface OutboxEventPublisher {

    boolean supports(OutboxEvent event);

    void publish(OutboxEvent event);
}

package com.phu.ecommerceapi.outbox.application;

/**
 * Publishers run after the outbox claim transaction commits. External side effects must be advisory
 * or idempotent using the stable {@link OutboxEvent#id()} because delivery is at least once.
 */
public interface OutboxEventPublisher {

    boolean supports(OutboxEvent event);

    void publish(OutboxEvent event);
}

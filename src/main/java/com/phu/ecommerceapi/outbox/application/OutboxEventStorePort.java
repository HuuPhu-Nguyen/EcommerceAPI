package com.phu.ecommerceapi.outbox.application;

public interface OutboxEventStorePort {

    void savePending(OutboxEvent event);
}

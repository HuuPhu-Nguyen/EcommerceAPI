package com.phu.ecommerceapi.inventory.application;

public interface StockEventPublisher {

    int publish(StockChangedSseEvent event);
}

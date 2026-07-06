package com.phu.ecommerceapi.order.domain;

public class InvalidOrderStateTransitionException extends RuntimeException {

    public InvalidOrderStateTransitionException(OrderStatus currentStatus, OrderStatus requestedStatus) {
        super("Cannot transition order from %s to %s".formatted(currentStatus, requestedStatus));
    }
}

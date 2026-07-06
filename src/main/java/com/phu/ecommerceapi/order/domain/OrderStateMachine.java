package com.phu.ecommerceapi.order.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(
                    OrderStatus.PAID,
                    OrderStatus.PAYMENT_FAILED,
                    OrderStatus.CANCELLED
            ),
            OrderStatus.PAID, Set.of(OrderStatus.REFUNDED),
            OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.CANCELLED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.REFUNDED, Set.of()
    );

    private OrderStateMachine() {
    }

    public static OrderStatus transition(OrderStatus currentStatus, OrderStatus requestedStatus) {
        Objects.requireNonNull(currentStatus, "current order status is required");
        Objects.requireNonNull(requestedStatus, "requested order status is required");

        if (currentStatus == requestedStatus) {
            return currentStatus;
        }
        if (!canTransition(currentStatus, requestedStatus)) {
            throw new InvalidOrderStateTransitionException(currentStatus, requestedStatus);
        }
        return requestedStatus;
    }

    public static boolean canTransition(OrderStatus currentStatus, OrderStatus requestedStatus) {
        Objects.requireNonNull(currentStatus, "current order status is required");
        Objects.requireNonNull(requestedStatus, "requested order status is required");

        return ALLOWED_TRANSITIONS
                .getOrDefault(currentStatus, Set.of())
                .contains(requestedStatus);
    }

    public static Set<OrderStatus> allowedTargets(OrderStatus currentStatus) {
        Objects.requireNonNull(currentStatus, "current order status is required");
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
    }
}

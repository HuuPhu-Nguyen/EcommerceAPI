package com.phu.ecommerceapi.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void pendingPaymentCanMoveToPaymentOutcomesOrCancellation() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void paidOrdersCanOnlyMoveToRefunded() {
        assertThat(OrderStateMachine.transition(OrderStatus.PAID, OrderStatus.REFUNDED))
                .isEqualTo(OrderStatus.REFUNDED);

        assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.PAID, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .hasMessage("Cannot transition order from PAID to CANCELLED");
    }

    @Test
    void terminalStatesRejectFurtherTransitions() {
        assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.CANCELLED, OrderStatus.PAID))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
        assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.REFUNDED, OrderStatus.PAID))
                .isInstanceOf(InvalidOrderStateTransitionException.class);
    }

    @Test
    void sameStateTransitionIsIdempotent() {
        assertThat(OrderStateMachine.transition(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_PAYMENT))
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}

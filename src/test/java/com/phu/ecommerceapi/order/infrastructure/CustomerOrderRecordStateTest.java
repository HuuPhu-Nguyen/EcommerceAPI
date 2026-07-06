package com.phu.ecommerceapi.order.infrastructure;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.order.domain.InvalidOrderStateTransitionException;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderRecordStateTest {

    @Test
    void orderRecordUsesStateMachineForValidPaymentAndRefundTransitions() {
        CustomerOrderRecord order = pendingOrder();

        order.markPaid();
        order.refund();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void orderRecordRejectsBypassingLifecycleRules() {
        CustomerOrderRecord order = pendingOrder();
        order.cancel();

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .hasMessage("Cannot transition order from CANCELLED to PAID");
    }

    private CustomerOrderRecord pendingOrder() {
        UserModel customer = UserModel.builder()
                .username("state-customer@example.com")
                .email("state-customer@example.com")
                .build();
        return CustomerOrderRecord.pendingPayment(customer, 1L, "USD");
    }
}

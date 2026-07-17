package com.phu.ecommerceapi.order.infrastructure;

import com.phu.ecommerceapi.catalog.infrastructure.ProductModel;
import com.phu.ecommerceapi.customer.infrastructure.UserModel;
import com.phu.ecommerceapi.order.domain.InvalidOrderStateTransitionException;
import com.phu.ecommerceapi.order.domain.OrderStatus;
import com.phu.ecommerceapi.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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

    @Test
    void orderRecordRejectsItemMoneyInDifferentCurrency() {
        CustomerOrderRecord order = pendingOrder();
        ProductModel product = ProductModel.builder()
                .name("Foreign currency item")
                .price(new BigDecimal("10.00"))
                .currency("EUR")
                .build();

        assertThatThrownBy(() -> order.addItem(product, 1, Money.of("10.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order item currency mismatch");
    }

    private CustomerOrderRecord pendingOrder() {
        UserModel customer = UserModel.builder()
                .identitySubject("state-customer-subject")
                .username("state-customer@example.com")
                .email("state-customer@example.com")
                .build();
        return CustomerOrderRecord.pendingPayment(customer, 1L, "USD");
    }
}

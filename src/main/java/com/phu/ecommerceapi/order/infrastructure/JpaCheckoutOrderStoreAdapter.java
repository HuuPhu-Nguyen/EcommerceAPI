package com.phu.ecommerceapi.order.infrastructure;

import com.phu.ecommerceapi.User.UserModel;
import com.phu.ecommerceapi.cart.application.CartCheckoutStatusPort;
import com.phu.ecommerceapi.cart.application.CartItemSnapshot;
import com.phu.ecommerceapi.cart.application.CartSnapshot;
import com.phu.ecommerceapi.checkout.application.CheckoutOrderItemSnapshot;
import com.phu.ecommerceapi.checkout.application.CheckoutOrderSnapshot;
import com.phu.ecommerceapi.checkout.application.CheckoutOrderStorePort;
import org.springframework.stereotype.Component;

@Component
public class JpaCheckoutOrderStoreAdapter implements CheckoutOrderStorePort, CartCheckoutStatusPort {

    private final CustomerOrderRepository orderRepository;

    public JpaCheckoutOrderStoreAdapter(CustomerOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public boolean existsByCartId(long cartId) {
        return orderRepository.existsByCartId(cartId);
    }

    @Override
    public boolean existsOrderForCart(long cartId) {
        return existsByCartId(cartId);
    }

    @Override
    public CheckoutOrderSnapshot createPendingPayment(CartSnapshot cart) {
        CustomerOrderRecord order = CustomerOrderRecord.pendingPayment(
                UserModel.reference(cart.ownerId(), cart.ownerIdentitySubject()),
                cart.id(),
                cart.currency()
        );
        for (CartItemSnapshot item : cart.items()) {
            order.addItem(item.productId(), item.productName(), item.quantity(), item.unitPrice());
        }
        return toSnapshot(orderRepository.save(order));
    }

    private CheckoutOrderSnapshot toSnapshot(CustomerOrderRecord order) {
        return new CheckoutOrderSnapshot(
                order.getId(),
                order.getCartId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                order.getItems()
                        .stream()
                        .map(this::toItemSnapshot)
                        .toList()
        );
    }

    private CheckoutOrderItemSnapshot toItemSnapshot(OrderItemRecord item) {
        return new CheckoutOrderItemSnapshot(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPriceAmount(),
                item.getLineTotalAmount()
        );
    }
}

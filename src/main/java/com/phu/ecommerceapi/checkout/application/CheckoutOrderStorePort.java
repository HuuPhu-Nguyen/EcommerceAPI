package com.phu.ecommerceapi.checkout.application;

import com.phu.ecommerceapi.cart.application.CartSnapshot;

public interface CheckoutOrderStorePort {

    boolean existsByCartId(long cartId);

    CheckoutOrderSnapshot createPendingPayment(CartSnapshot cart);
}

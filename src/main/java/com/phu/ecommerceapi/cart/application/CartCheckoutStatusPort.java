package com.phu.ecommerceapi.cart.application;

public interface CartCheckoutStatusPort {

    boolean existsOrderForCart(long cartId);
}

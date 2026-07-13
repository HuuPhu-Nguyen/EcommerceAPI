package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.customer.application.CustomerIdentity;

import java.util.Optional;
import java.util.function.Function;

public interface CartPersistencePort {

    CartSnapshot create(CustomerIdentity owner);

    Optional<CartSnapshot> findWithItemsById(long cartId);

    <T> Optional<T> updateWithItemsForMutation(long cartId, Function<MutableCart, T> mutation);

    <T> Optional<T> updateWithItemsForCheckout(long cartId, Function<MutableCart, T> mutation);
}

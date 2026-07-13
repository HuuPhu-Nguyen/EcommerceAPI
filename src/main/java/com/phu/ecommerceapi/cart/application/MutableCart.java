package com.phu.ecommerceapi.cart.application;

import com.phu.ecommerceapi.catalog.application.CartProductSnapshot;

public interface MutableCart {

    CartSnapshot snapshot();

    int quantityForProduct(long productId);

    void addItem(CartProductSnapshot product, int quantity);

    void updateItemQuantity(CartProductSnapshot product, int quantity);

    void removeItem(long productId);

    void clear();
}

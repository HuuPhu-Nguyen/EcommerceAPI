package com.phu.ecommerceapi.catalog.application;

import java.util.Optional;

public interface CartProductLookupPort {

    Optional<CartProductSnapshot> findActiveForCart(long productId);
}

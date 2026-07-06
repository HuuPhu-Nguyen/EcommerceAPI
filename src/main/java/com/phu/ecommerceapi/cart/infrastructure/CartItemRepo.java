package com.phu.ecommerceapi.cart.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepo extends JpaRepository<CartItemModel, Long> {
}

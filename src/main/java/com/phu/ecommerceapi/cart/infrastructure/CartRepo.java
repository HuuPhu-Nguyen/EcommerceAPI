package com.phu.ecommerceapi.cart.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepo extends JpaRepository<CartModel, Long> {

    @EntityGraph(attributePaths = {"owner", "items", "items.productModel"})
    Optional<CartModel> findWithItemsById(long id);
}

package com.phu.ecommerceapi.cart.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepo extends JpaRepository<CartModel, Long> {

    @EntityGraph(attributePaths = {"owner", "items", "items.productModel"})
    Optional<CartModel> findWithItemsById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"owner", "items", "items.productModel"})
    @Query("select cart from CartModel cart where cart.id = :id")
    Optional<CartModel> findForCheckoutById(@Param("id") long id);
}

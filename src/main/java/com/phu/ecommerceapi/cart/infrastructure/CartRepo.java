package com.phu.ecommerceapi.cart.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepo extends JpaRepository<CartModel, Long> {

    @EntityGraph(attributePaths = {"owner", "items", "items.productModel"})
    Optional<CartModel> findWithItemsById(long id);

    @Query(
            value = """
                    select id
                    from cart_model
                    where id = :id
                    for update
                    """,
            nativeQuery = true
    )
    Optional<Long> lockById(@Param("id") long id);
}

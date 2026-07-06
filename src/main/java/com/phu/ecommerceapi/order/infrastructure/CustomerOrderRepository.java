package com.phu.ecommerceapi.order.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrderRecord, UUID> {

    boolean existsByCartId(long cartId);

    @Query("""
            select customerOrder
            from CustomerOrderRecord customerOrder
            join fetch customerOrder.customer
            where customerOrder.id = :orderId
            """)
    Optional<CustomerOrderRecord> findWithCustomerById(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select customerOrder
            from CustomerOrderRecord customerOrder
            join fetch customerOrder.customer
            where customerOrder.id = :orderId
            """)
    Optional<CustomerOrderRecord> findForPaymentById(@Param("orderId") UUID orderId);
}

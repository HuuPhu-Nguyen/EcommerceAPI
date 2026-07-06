package com.phu.ecommerceapi.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrderRecord, UUID> {

    boolean existsByCartId(long cartId);
}

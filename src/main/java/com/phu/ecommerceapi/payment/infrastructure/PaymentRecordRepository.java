package com.phu.ecommerceapi.payment.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<PaymentRecord> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from PaymentRecord payment
            join fetch payment.order customerOrder
            join fetch customerOrder.customer
            where payment.id = :paymentId
            """)
    Optional<PaymentRecord> findForUpdateById(@Param("paymentId") UUID paymentId);
}

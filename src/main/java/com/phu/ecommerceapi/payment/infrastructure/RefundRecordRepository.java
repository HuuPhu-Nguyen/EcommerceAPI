package com.phu.ecommerceapi.payment.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, UUID> {

    boolean existsByPaymentId(UUID paymentId);

    Optional<RefundRecord> findByPaymentId(UUID paymentId);

    Optional<RefundRecord> findByProviderRefundId(String providerRefundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refund
            from RefundRecord refund
            join fetch refund.payment payment
            join fetch payment.order customerOrder
            join fetch customerOrder.customer
            where refund.id = :refundId
            """)
    Optional<RefundRecord> findForUpdateById(@Param("refundId") UUID refundId);
}

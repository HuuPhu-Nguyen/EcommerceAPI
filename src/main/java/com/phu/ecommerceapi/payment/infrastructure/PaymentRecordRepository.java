package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.payment.domain.PaymentStatus;
import com.phu.ecommerceapi.reconciliation.application.PaymentReconciliationItem;
import com.phu.ecommerceapi.reconciliation.application.ProviderReconciliationReference;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.PaymentReconciliationItem(
                payment.id,
                payment.amount,
                payment.currency,
                payment.status,
                payment.providerCode,
                payment.providerPaymentId
            )
            from PaymentRecord payment
            where (:afterIdExclusive is null or payment.id > :afterIdExclusive)
            order by payment.id asc
            """)
    List<PaymentReconciliationItem> findPageForReconciliation(
            @Param("afterIdExclusive") UUID afterIdExclusive,
            Pageable pageable
    );

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.PaymentReconciliationItem(
                payment.id,
                payment.amount,
                payment.currency,
                payment.status,
                payment.providerCode,
                payment.providerPaymentId
            )
            from PaymentRecord payment
            where payment.id in :paymentIds
            """)
    List<PaymentReconciliationItem> findByIdsForReconciliation(@Param("paymentIds") Collection<UUID> paymentIds);

    @Query("""
            select payment.id
            from PaymentRecord payment
            where payment.id in :paymentIds
            """)
    List<UUID> findExistingIdsForReconciliation(@Param("paymentIds") Collection<UUID> paymentIds);

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.ProviderReconciliationReference(
                payment.providerCode,
                payment.providerPaymentId
            )
            from PaymentRecord payment
            where payment.providerCode in :providerCodes
              and payment.providerPaymentId in :providerPaymentIds
            """)
    List<ProviderReconciliationReference> findProviderReferencesForReconciliation(
            @Param("providerCodes") Collection<String> providerCodes,
            @Param("providerPaymentIds") Collection<String> providerPaymentIds
    );

    Optional<PaymentRecord> findByOrderId(UUID orderId);

    Optional<PaymentRecord> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    @Query("""
            select count(payment) > 0
            from PaymentRecord payment
            where payment.order.id = :orderId
              and payment.status in :statuses
            """)
    boolean existsByOrderIdAndStatusIn(
            @Param("orderId") UUID orderId,
            @Param("statuses") Collection<PaymentStatus> statuses
    );

    Optional<PaymentRecord> findByProviderCodeAndProviderPaymentId(String providerCode, String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from PaymentRecord payment
            where payment.providerCode = :providerCode
              and payment.providerPaymentId = :providerPaymentId
            """)
    Optional<PaymentRecord> findForUpdateByProviderCodeAndProviderPaymentId(
            @Param("providerCode") String providerCode,
            @Param("providerPaymentId") String providerPaymentId
    );

    @Query("""
            select payment
            from PaymentRecord payment
            join fetch payment.order customerOrder
            join fetch customerOrder.customer
            where payment.id = :paymentId
            """)
    Optional<PaymentRecord> findWithOrderById(@Param("paymentId") UUID paymentId);

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

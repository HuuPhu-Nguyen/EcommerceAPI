package com.phu.ecommerceapi.payment.infrastructure;

import com.phu.ecommerceapi.reconciliation.application.ProviderReconciliationReference;
import com.phu.ecommerceapi.reconciliation.application.RefundReconciliationItem;
import com.phu.ecommerceapi.payment.application.RefundWebhookAttempt;
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

public interface RefundRecordRepository extends JpaRepository<RefundRecord, UUID> {

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.RefundReconciliationItem(
                refund.id,
                payment.id,
                refund.amount,
                refund.currency,
                refund.status,
                refund.providerCode,
                refund.providerRefundId
            )
            from RefundRecord refund
            join refund.payment payment
            where (:afterIdExclusive is null or refund.id > :afterIdExclusive)
            order by refund.id asc
            """)
    List<RefundReconciliationItem> findPageForReconciliation(
            @Param("afterIdExclusive") UUID afterIdExclusive,
            Pageable pageable
    );

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.RefundReconciliationItem(
                refund.id,
                payment.id,
                refund.amount,
                refund.currency,
                refund.status,
                refund.providerCode,
                refund.providerRefundId
            )
            from RefundRecord refund
            join refund.payment payment
            where refund.id in :refundIds
            """)
    List<RefundReconciliationItem> findByIdsForReconciliation(@Param("refundIds") Collection<UUID> refundIds);

    @Query("""
            select refund.id
            from RefundRecord refund
            where refund.id in :refundIds
            """)
    List<UUID> findExistingIdsForReconciliation(@Param("refundIds") Collection<UUID> refundIds);

    @Query("""
            select new com.phu.ecommerceapi.reconciliation.application.ProviderReconciliationReference(
                refund.providerCode,
                refund.providerRefundId
            )
            from RefundRecord refund
            where refund.providerCode in :providerCodes
              and refund.providerRefundId in :providerRefundIds
            """)
    List<ProviderReconciliationReference> findProviderReferencesForReconciliation(
            @Param("providerCodes") Collection<String> providerCodes,
            @Param("providerRefundIds") Collection<String> providerRefundIds
    );

    boolean existsByPaymentId(UUID paymentId);

    Optional<RefundRecord> findByPaymentId(UUID paymentId);

    Optional<RefundRecord> findByProviderCodeAndProviderRefundId(String providerCode, String providerRefundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refund
            from RefundRecord refund
            where refund.providerCode = :providerCode
              and refund.providerRefundId = :providerRefundId
            """)
    Optional<RefundRecord> findForUpdateByProviderCodeAndProviderRefundId(
            @Param("providerCode") String providerCode,
            @Param("providerRefundId") String providerRefundId
    );

    @Query("""
            select refund
            from RefundRecord refund
            join fetch refund.payment payment
            join fetch payment.order
            where refund.id = :refundId
            """)
    Optional<RefundRecord> findWithPaymentById(@Param("refundId") UUID refundId);

    @Query("""
            select new com.phu.ecommerceapi.payment.application.RefundWebhookAttempt(
                refund.id,
                refund.status,
                refund.providerRefundId
            )
            from RefundRecord refund
            where refund.id = :refundId
              and refund.providerCode = :providerCode
              and (
                :providerRefundId is null
                or refund.providerRefundId is null
                or refund.providerRefundId = :providerRefundId
              )
            """)
    Optional<RefundWebhookAttempt> findWebhookAttemptByIdAndProvider(
            @Param("refundId") UUID refundId,
            @Param("providerCode") String providerCode,
            @Param("providerRefundId") String providerRefundId
    );

    @Query("""
            select new com.phu.ecommerceapi.payment.application.RefundWebhookAttempt(
                refund.id,
                refund.status,
                refund.providerRefundId
            )
            from RefundRecord refund
            where refund.providerCode = :providerCode
              and refund.providerRefundId = :providerRefundId
            """)
    Optional<RefundWebhookAttempt> findWebhookAttemptByProviderReference(
            @Param("providerCode") String providerCode,
            @Param("providerRefundId") String providerRefundId
    );

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

package com.phu.ecommerceapi.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentIdempotencyRecordRepository extends JpaRepository<PaymentIdempotencyRecord, Long> {

    Optional<PaymentIdempotencyRecord> findByCustomerIdAndEndpointAndOperationAndIdempotencyKey(
            long customerId,
            String endpoint,
            String operation,
            String idempotencyKey
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into payment_idempotency_record (
                customer_id,
                endpoint,
                operation,
                idempotency_key,
                request_hash,
                status,
                in_progress_expires_at,
                recovery_status,
                created_at,
                version
            )
            values (
                :customerId,
                :endpoint,
                :operation,
                :idempotencyKey,
                :requestHash,
                'IN_PROGRESS',
                :inProgressExpiresAt,
                'NOT_REQUIRED',
                :createdAt,
                0
            )
            on conflict (customer_id, endpoint, operation, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertInProgress(
            @Param("customerId") long customerId,
            @Param("endpoint") String endpoint,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("inProgressExpiresAt") OffsetDateTime inProgressExpiresAt,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Query(value = """
            select *
            from payment_idempotency_record
            where status = 'IN_PROGRESS'
              and in_progress_expires_at is not null
              and in_progress_expires_at <= :now
              and resource_type is not null
              and (recovery_status is null or recovery_status = 'NOT_REQUIRED')
            order by id
            for update skip locked
            limit :limit
            """, nativeQuery = true)
    List<PaymentIdempotencyRecord> findExpiredForRecovery(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );
}

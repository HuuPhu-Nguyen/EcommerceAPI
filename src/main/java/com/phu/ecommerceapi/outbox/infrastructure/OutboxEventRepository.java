package com.phu.ecommerceapi.outbox.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventRecord, UUID> {

    long countByStatus(OutboxEventStatus status);

    @Query("""
            select min(event.createdAt)
            from OutboxEventRecord event
            where event.status in :statuses
            """)
    Optional<Instant> oldestCreatedAtForStatuses(@Param("statuses") List<OutboxEventStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from OutboxEventRecord event
            where event.id = :id
            """)
    Optional<OutboxEventRecord> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(
            value = """
                    UPDATE outbox_event
                    SET status = 'FAILED',
                        attempts = attempts + 1,
                        next_attempt_at = :now,
                        last_error = :lastError,
                        locked_at = NULL
                    WHERE status = 'PROCESSING'
                      AND locked_at < :timedOutBefore
                    """,
            nativeQuery = true
    )
    int resetTimedOutProcessing(
            @Param("timedOutBefore") Instant timedOutBefore,
            @Param("now") Instant now,
            @Param("lastError") String lastError
    );

    @Query(
            value = """
                    SELECT *
                    FROM outbox_event
                    WHERE status IN ('PENDING', 'FAILED')
                      AND next_attempt_at <= :now
                    ORDER BY created_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEventRecord> findDueForProcessing(
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}

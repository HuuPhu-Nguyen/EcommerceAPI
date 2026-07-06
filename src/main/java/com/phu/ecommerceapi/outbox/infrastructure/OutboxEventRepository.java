package com.phu.ecommerceapi.outbox.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
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

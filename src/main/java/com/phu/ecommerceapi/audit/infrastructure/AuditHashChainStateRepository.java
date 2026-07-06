package com.phu.ecommerceapi.audit.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditHashChainStateRepository extends JpaRepository<AuditHashChainStateRecord, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select state
            from AuditHashChainStateRecord state
            where state.id = :id
            """)
    Optional<AuditHashChainStateRecord> findForUpdateById(@Param("id") short id);
}

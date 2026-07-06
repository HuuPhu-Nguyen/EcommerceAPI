package com.phu.ecommerceapi.audit.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventRecord, Long> {

    List<AuditEventRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditEventRecord> findAllByOrderByIdAsc();
}

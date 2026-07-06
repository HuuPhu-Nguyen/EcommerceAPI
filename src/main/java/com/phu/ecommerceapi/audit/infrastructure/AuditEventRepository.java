package com.phu.ecommerceapi.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventRecord, Long> {
}

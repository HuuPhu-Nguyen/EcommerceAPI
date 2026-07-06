package com.phu.ecommerceapi.audit.infrastructure;

import com.phu.ecommerceapi.audit.application.AuditEventCommand;
import com.phu.ecommerceapi.audit.application.AuditEventRecorder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JpaAuditEventRecorder implements AuditEventRecorder {

    private final AuditEventRepository auditEventRepository;

    public JpaAuditEventRecorder(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void record(AuditEventCommand command) {
        auditEventRepository.save(AuditEventRecord.from(command, Instant.now()));
    }
}

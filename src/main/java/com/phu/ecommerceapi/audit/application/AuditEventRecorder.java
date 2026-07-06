package com.phu.ecommerceapi.audit.application;

public interface AuditEventRecorder {

    void record(AuditEventCommand command);
}

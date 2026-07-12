ALTER TABLE audit_event
    ADD COLUMN external_correlation_id VARCHAR(64);

CREATE INDEX idx_audit_event_external_correlation_id
    ON audit_event (external_correlation_id)
    WHERE external_correlation_id IS NOT NULL;

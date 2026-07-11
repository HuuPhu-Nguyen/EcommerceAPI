ALTER TABLE provider_webhook_event
    ADD COLUMN provider_event_created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN provider_event_type VARCHAR(120),
    ADD COLUMN provider_object_id VARCHAR(255),
    ADD COLUMN provider_object_type VARCHAR(80);

ALTER TABLE provider_webhook_event
    DROP CONSTRAINT chk_provider_webhook_event_type,
    ADD CONSTRAINT chk_provider_webhook_event_type
        CHECK (event_type IN (
            'PAYMENT_SUCCEEDED',
            'PAYMENT_FAILED',
            'REFUND_SUCCEEDED',
            'REFUND_FAILED',
            'UNSUPPORTED'
        ));

ALTER TABLE provider_webhook_event
    DROP CONSTRAINT chk_provider_webhook_processing_status,
    ADD CONSTRAINT chk_provider_webhook_processing_status
        CHECK (processing_status IN (
            'RECEIVED',
            'PROCESSED',
            'IGNORED',
            'REJECTED',
            'RECONCILIATION_REQUIRED'
        ));

CREATE INDEX idx_provider_webhook_event_provider_created_at
    ON provider_webhook_event (provider_name, provider_event_created_at)
    WHERE provider_event_created_at IS NOT NULL;

CREATE INDEX idx_provider_webhook_event_provider_object
    ON provider_webhook_event (provider_name, provider_object_id)
    WHERE provider_object_id IS NOT NULL;

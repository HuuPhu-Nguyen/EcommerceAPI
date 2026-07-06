CREATE TABLE provider_webhook_event (
    id UUID PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    processing_status VARCHAR(40) NOT NULL,
    processing_message VARCHAR(500),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_provider_webhook_event_provider_event
        UNIQUE (provider_name, provider_event_id),
    CONSTRAINT chk_provider_webhook_event_type
        CHECK (event_type IN ('PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'REFUND_SUCCEEDED', 'REFUND_FAILED')),
    CONSTRAINT chk_provider_webhook_processing_status
        CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'REJECTED')),
    CONSTRAINT chk_provider_webhook_processed_at
        CHECK (processing_status = 'RECEIVED' OR processed_at IS NOT NULL)
);

CREATE INDEX idx_provider_webhook_event_received_at
    ON provider_webhook_event (received_at);

CREATE INDEX idx_provider_webhook_event_status_received_at
    ON provider_webhook_event (processing_status, received_at);

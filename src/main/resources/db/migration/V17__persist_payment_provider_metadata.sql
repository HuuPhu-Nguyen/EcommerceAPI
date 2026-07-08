ALTER TABLE payment_record
    ADD COLUMN provider_code VARCHAR(50),
    ADD COLUMN provider_idempotency_key VARCHAR(255);

UPDATE payment_record
SET provider_code = 'fake'
WHERE provider_code IS NULL;

UPDATE payment_record
SET provider_idempotency_key = 'payment:fake:' || customer_id || ':' || order_id || ':' || idempotency_key
WHERE provider_idempotency_key IS NULL;

ALTER TABLE payment_record
    ALTER COLUMN provider_code SET NOT NULL,
    ALTER COLUMN provider_idempotency_key SET NOT NULL;

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_record_provider_code
        CHECK (provider_code IN ('fake', 'stripe'));

CREATE UNIQUE INDEX ux_payment_record_provider_idempotency_key
    ON payment_record (provider_code, provider_idempotency_key);

DROP INDEX ux_payment_record_order_id;

DROP INDEX ux_payment_record_provider_payment_id;

CREATE UNIQUE INDEX ux_payment_record_provider_payment_id
    ON payment_record (provider_code, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE UNIQUE INDEX ux_payment_record_order_active
    ON payment_record (order_id)
    WHERE status IN ('PENDING', 'PROVIDER_TIMEOUT');

CREATE UNIQUE INDEX ux_payment_record_order_success
    ON payment_record (order_id)
    WHERE status IN ('SUCCEEDED', 'REFUNDED');

CREATE INDEX idx_payment_record_order_status_created_at
    ON payment_record (order_id, status, created_at);

CREATE INDEX idx_payment_record_provider_status_created_at
    ON payment_record (provider_code, status, created_at);

ALTER TABLE refund_record
    ADD COLUMN provider_code VARCHAR(50),
    ADD COLUMN provider_idempotency_key VARCHAR(255);

UPDATE refund_record refund
SET provider_code = payment.provider_code
FROM payment_record payment
WHERE refund.payment_id = payment.id
  AND refund.provider_code IS NULL;

UPDATE refund_record
SET provider_code = 'fake'
WHERE provider_code IS NULL;

UPDATE refund_record
SET provider_idempotency_key = 'refund:' || provider_code || ':' || customer_id || ':' || payment_id || ':' || idempotency_key
WHERE provider_idempotency_key IS NULL;

ALTER TABLE refund_record
    ALTER COLUMN provider_code SET NOT NULL,
    ALTER COLUMN provider_idempotency_key SET NOT NULL;

ALTER TABLE refund_record
    ADD CONSTRAINT chk_refund_record_provider_code
        CHECK (provider_code IN ('fake', 'stripe'));

CREATE UNIQUE INDEX ux_refund_record_provider_idempotency_key
    ON refund_record (provider_code, provider_idempotency_key);

DROP INDEX ux_refund_record_provider_refund_id;

CREATE UNIQUE INDEX ux_refund_record_provider_refund_id
    ON refund_record (provider_code, provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;

ALTER TABLE payment_idempotency_record
    ADD COLUMN resource_type VARCHAR(30),
    ADD COLUMN resource_id UUID,
    ADD COLUMN provider_code VARCHAR(50),
    ADD COLUMN provider_idempotency_key VARCHAR(255),
    ADD COLUMN in_progress_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_recovery_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN recovery_status VARCHAR(40);

ALTER TABLE payment_idempotency_record
    ADD CONSTRAINT chk_payment_idempotency_resource_type
        CHECK (resource_type IS NULL OR resource_type IN ('PAYMENT', 'REFUND')),
    ADD CONSTRAINT chk_payment_idempotency_provider_code
        CHECK (provider_code IS NULL OR provider_code IN ('fake', 'stripe')),
    ADD CONSTRAINT chk_payment_idempotency_recovery_status
        CHECK (
            recovery_status IS NULL
            OR recovery_status IN ('NOT_REQUIRED', 'PENDING_RECONCILIATION', 'RECOVERED', 'MANUAL_REVIEW')
        );

CREATE INDEX idx_payment_idempotency_recovery
    ON payment_idempotency_record (status, in_progress_expires_at)
    WHERE status = 'IN_PROGRESS';

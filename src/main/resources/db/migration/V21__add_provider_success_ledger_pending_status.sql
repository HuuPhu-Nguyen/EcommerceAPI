ALTER TABLE payment_record
    DROP CONSTRAINT chk_payment_record_status;

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_record_status
        CHECK (status IN (
            'PENDING',
            'PROVIDER_SUCCEEDED_LEDGER_PENDING',
            'SUCCEEDED',
            'FAILED',
            'PROVIDER_TIMEOUT',
            'REFUNDED'
        ));

ALTER TABLE payment_record
    DROP CONSTRAINT chk_payment_record_terminal_completed_at;

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_record_terminal_completed_at
        CHECK (status IN ('PENDING', 'PROVIDER_SUCCEEDED_LEDGER_PENDING') OR completed_at IS NOT NULL);

ALTER TABLE payment_record
    DROP CONSTRAINT chk_payment_record_success_provider_id;

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_record_success_provider_id
        CHECK (
            status NOT IN ('PROVIDER_SUCCEEDED_LEDGER_PENDING', 'SUCCEEDED', 'REFUNDED')
            OR provider_payment_id IS NOT NULL
        );

DROP INDEX IF EXISTS ux_payment_record_order_active;

CREATE UNIQUE INDEX ux_payment_record_order_active
    ON payment_record (order_id)
    WHERE status IN ('PENDING', 'PROVIDER_SUCCEEDED_LEDGER_PENDING', 'PROVIDER_TIMEOUT');

ALTER TABLE refund_record
    DROP CONSTRAINT chk_refund_record_status;

ALTER TABLE refund_record
    ADD CONSTRAINT chk_refund_record_status
        CHECK (status IN (
            'PENDING',
            'PROVIDER_SUCCEEDED_LEDGER_PENDING',
            'SUCCEEDED',
            'FAILED',
            'PROVIDER_TIMEOUT'
        ));

ALTER TABLE refund_record
    DROP CONSTRAINT chk_refund_record_terminal_completed_at;

ALTER TABLE refund_record
    ADD CONSTRAINT chk_refund_record_terminal_completed_at
        CHECK (status IN ('PENDING', 'PROVIDER_SUCCEEDED_LEDGER_PENDING') OR completed_at IS NOT NULL);

ALTER TABLE refund_record
    DROP CONSTRAINT chk_refund_record_success_provider_id;

ALTER TABLE refund_record
    ADD CONSTRAINT chk_refund_record_success_provider_id
        CHECK (
            status NOT IN ('PROVIDER_SUCCEEDED_LEDGER_PENDING', 'SUCCEEDED')
            OR provider_refund_id IS NOT NULL
        );

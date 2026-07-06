ALTER TABLE payment_record
    DROP CONSTRAINT chk_payment_record_status;

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_record_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'PROVIDER_TIMEOUT', 'REFUNDED'));

ALTER TABLE payment_record
    DROP CONSTRAINT chk_payment_record_success_provider_id;

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_record_success_provider_id
        CHECK (status NOT IN ('SUCCEEDED', 'REFUNDED') OR provider_payment_id IS NOT NULL);

CREATE TABLE refund_record (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    order_id UUID NOT NULL,
    customer_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_refund_id VARCHAR(255),
    provider_status VARCHAR(40),
    failure_code VARCHAR(100),
    provider_message VARCHAR(500),
    idempotency_key VARCHAR(128) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_record_payment
        FOREIGN KEY (payment_id) REFERENCES payment_record (id),
    CONSTRAINT fk_refund_record_order
        FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT fk_refund_record_customer
        FOREIGN KEY (customer_id) REFERENCES user_model (id),
    CONSTRAINT chk_refund_record_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'PROVIDER_TIMEOUT')),
    CONSTRAINT chk_refund_record_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_refund_record_terminal_completed_at
        CHECK (status = 'PENDING' OR completed_at IS NOT NULL),
    CONSTRAINT chk_refund_record_success_provider_id
        CHECK (status <> 'SUCCEEDED' OR provider_refund_id IS NOT NULL),
    CONSTRAINT chk_refund_record_failure_code
        CHECK (status NOT IN ('FAILED', 'PROVIDER_TIMEOUT') OR failure_code IS NOT NULL)
);

CREATE UNIQUE INDEX ux_refund_record_payment_id
    ON refund_record (payment_id);

CREATE UNIQUE INDEX ux_refund_record_provider_refund_id
    ON refund_record (provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;

CREATE INDEX idx_refund_record_customer_created_at
    ON refund_record (customer_id, created_at);

CREATE INDEX idx_refund_record_status_created_at
    ON refund_record (status, created_at);

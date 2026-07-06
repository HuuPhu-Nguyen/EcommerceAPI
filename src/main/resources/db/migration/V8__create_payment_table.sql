CREATE TABLE payment_record (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(255),
    provider_status VARCHAR(40),
    failure_code VARCHAR(100),
    provider_message VARCHAR(500),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_record_order
        FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT fk_payment_record_customer
        FOREIGN KEY (customer_id) REFERENCES user_model (id),
    CONSTRAINT chk_payment_record_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'PROVIDER_TIMEOUT')),
    CONSTRAINT chk_payment_record_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_payment_record_terminal_completed_at
        CHECK (status = 'PENDING' OR completed_at IS NOT NULL),
    CONSTRAINT chk_payment_record_success_provider_id
        CHECK (status <> 'SUCCEEDED' OR provider_payment_id IS NOT NULL),
    CONSTRAINT chk_payment_record_failure_code
        CHECK (status NOT IN ('FAILED', 'PROVIDER_TIMEOUT') OR failure_code IS NOT NULL)
);

CREATE UNIQUE INDEX ux_payment_record_order_id
    ON payment_record (order_id);

CREATE UNIQUE INDEX ux_payment_record_provider_payment_id
    ON payment_record (provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE INDEX idx_payment_record_customer_created_at
    ON payment_record (customer_id, created_at);

CREATE INDEX idx_payment_record_status_created_at
    ON payment_record (status, created_at);

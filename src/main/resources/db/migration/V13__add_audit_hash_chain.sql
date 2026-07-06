ALTER TABLE audit_event
    ADD COLUMN previous_hash VARCHAR(64);

ALTER TABLE audit_event
    ADD COLUMN event_hash VARCHAR(64);

CREATE INDEX idx_audit_event_event_hash
    ON audit_event (event_hash);

CREATE TABLE audit_hash_chain_state (
    id SMALLINT PRIMARY KEY,
    latest_hash VARCHAR(64),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_audit_hash_chain_state_singleton
        CHECK (id = 1)
);

INSERT INTO audit_hash_chain_state (id, latest_hash, updated_at)
VALUES (1, NULL, CURRENT_TIMESTAMP);

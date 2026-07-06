ALTER TABLE audit_event
    ADD COLUMN request_id VARCHAR(100);

ALTER TABLE audit_event
    ADD COLUMN ip_address VARCHAR(100);

ALTER TABLE audit_event
    ADD COLUMN user_agent VARCHAR(500);

UPDATE audit_event
SET request_id = 'unknown',
    ip_address = 'unknown',
    user_agent = 'unknown';

ALTER TABLE audit_event
    ALTER COLUMN request_id SET NOT NULL;

ALTER TABLE audit_event
    ALTER COLUMN ip_address SET NOT NULL;

ALTER TABLE audit_event
    ALTER COLUMN user_agent SET NOT NULL;

CREATE INDEX idx_audit_event_request_id
    ON audit_event (request_id);

CREATE INDEX idx_audit_event_action_created_at
    ON audit_event (action, created_at);

CREATE INDEX idx_audit_event_actor_created_at
    ON audit_event (actor_subject, created_at);

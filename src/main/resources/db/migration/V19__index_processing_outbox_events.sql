CREATE INDEX idx_outbox_event_processing_locked_at
    ON outbox_event (locked_at)
    WHERE status = 'PROCESSING';

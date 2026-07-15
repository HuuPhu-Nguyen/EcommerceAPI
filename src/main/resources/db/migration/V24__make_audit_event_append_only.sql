DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM audit_event
        WHERE event_hash IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot make audit_event append-only while unhashed audit events exist';
    END IF;
END $$;

ALTER TABLE audit_event
    ALTER COLUMN event_hash SET NOT NULL;

CREATE OR REPLACE FUNCTION reject_audit_event_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();

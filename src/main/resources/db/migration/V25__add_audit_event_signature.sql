ALTER TABLE audit_event
    ADD COLUMN event_signature VARCHAR(64);

CREATE OR REPLACE FUNCTION reject_unsigned_audit_event_insert()
RETURNS trigger AS $$
BEGIN
    IF NEW.event_hash IS NULL OR btrim(NEW.event_hash) = '' THEN
        RAISE EXCEPTION 'audit event hash is required';
    END IF;
    IF NEW.event_signature IS NULL OR btrim(NEW.event_signature) = '' THEN
        RAISE EXCEPTION 'audit event signature is required';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_require_signature
    BEFORE INSERT ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_unsigned_audit_event_insert();

ALTER TABLE product_model
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_product_model_active_name ON product_model (active, name);

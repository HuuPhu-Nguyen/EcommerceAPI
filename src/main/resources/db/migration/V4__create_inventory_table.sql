CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY,
    available_quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id) REFERENCES product_model (product_id),
    CONSTRAINT chk_inventory_available_non_negative
        CHECK (available_quantity >= 0),
    CONSTRAINT chk_inventory_reserved_non_negative
        CHECK (reserved_quantity >= 0)
);

INSERT INTO inventory (product_id, available_quantity, reserved_quantity)
SELECT product_id, GREATEST(FLOOR(stock)::INTEGER, 0), 0
FROM product_model;

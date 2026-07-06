ALTER TABLE cart_model
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE cart_model
    ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE cart_item_model
    ALTER COLUMN cart_id SET NOT NULL;

ALTER TABLE cart_item_model
    ALTER COLUMN product_id SET NOT NULL;

ALTER TABLE cart_model
    ADD CONSTRAINT chk_cart_total_non_negative CHECK (total >= 0);

ALTER TABLE cart_item_model
    ADD CONSTRAINT chk_cart_item_quantity_positive CHECK (quantity > 0);

CREATE UNIQUE INDEX ux_cart_item_cart_product
    ON cart_item_model (cart_id, product_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM product_model product
        LEFT JOIN inventory inventory
            ON inventory.product_id = product.product_id
        WHERE inventory.product_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot drop product_model.stock while product rows without inventory exist';
    END IF;
END $$;

ALTER TABLE product_model
    DROP COLUMN stock;

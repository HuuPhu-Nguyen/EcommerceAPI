INSERT INTO user_model (
    id,
    identity_subject,
    username,
    first_name,
    last_name,
    email,
    phone,
    address
)
VALUES (
    1001,
    '11111111-1111-1111-1111-111111111111',
    'customer@example.com',
    'Casey',
    'Customer',
    'customer@example.com',
    '+1-555-0100',
    '100 Demo Street'
)
ON CONFLICT (id) DO UPDATE
SET identity_subject = EXCLUDED.identity_subject,
    username = EXCLUDED.username,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    address = EXCLUDED.address;

INSERT INTO product_model (
    product_id,
    name,
    price,
    stock,
    active,
    currency
)
VALUES
    (501, 'Hardware Security Key', 74.99, 12, true, 'USD'),
    (502, 'Encrypted Backup Drive', 129.50, 8, true, 'USD')
ON CONFLICT (product_id) DO UPDATE
SET name = EXCLUDED.name,
    price = EXCLUDED.price,
    stock = EXCLUDED.stock,
    active = EXCLUDED.active,
    currency = EXCLUDED.currency;

INSERT INTO inventory (
    product_id,
    available_quantity,
    reserved_quantity
)
VALUES
    (501, 12, 0),
    (502, 8, 0)
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity,
    reserved_quantity = EXCLUDED.reserved_quantity;

SELECT setval(
    pg_get_serial_sequence('user_model', 'id'),
    GREATEST((SELECT MAX(id) FROM user_model), 1),
    true
);

SELECT setval(
    pg_get_serial_sequence('product_model', 'product_id'),
    GREATEST((SELECT MAX(product_id) FROM product_model), 1),
    true
);

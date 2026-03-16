-- ============================================================
-- V2: Seed sample products and inventory
-- ============================================================

INSERT INTO products (id, sku, name)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'A100', 'Widget Alpha'),
    ('a1000000-0000-0000-0000-000000000002', 'B200', 'Gadget Beta'),
    ('a1000000-0000-0000-0000-000000000003', 'C300', 'Component Gamma')
ON CONFLICT (sku) DO NOTHING;

INSERT INTO inventory (product_id, total_stock, reserved_stock, version)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 100, 0, 0),
    ('a1000000-0000-0000-0000-000000000002', 250, 0, 0),
    ('a1000000-0000-0000-0000-000000000003',  50, 0, 0)
ON CONFLICT (product_id) DO NOTHING;

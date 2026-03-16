-- ============================================================
-- V1: Initial schema
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Products catalogue
CREATE TABLE IF NOT EXISTS products (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sku        VARCHAR(64) UNIQUE NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Inventory per product (one row per product)
CREATE TABLE IF NOT EXISTS inventory (
    id             UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID    NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    total_stock    INT     NOT NULL CHECK (total_stock >= 0),
    reserved_stock INT     NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    version        BIGINT  NOT NULL DEFAULT 0,
    UNIQUE (product_id),
    CONSTRAINT reserved_lte_total CHECK (reserved_stock <= total_stock)
);

-- Reservations header
CREATE TABLE IF NOT EXISTS reservations (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   VARCHAR(128) NOT NULL,
    status     VARCHAR(32)  NOT NULL CHECK (status IN ('PENDING','CONFIRMED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reservations_order_id  ON reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_reservations_status    ON reservations(status);

-- Line items per reservation
CREATE TABLE IF NOT EXISTS reservation_items (
    id             UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID    NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku            VARCHAR(64) NOT NULL,
    quantity       INT     NOT NULL CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_reservation_items_rid ON reservation_items(reservation_id);

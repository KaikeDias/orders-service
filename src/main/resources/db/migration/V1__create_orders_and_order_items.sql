CREATE TABLE orders (
    id uuid PRIMARY KEY,
    status text NOT NULL,
    currency text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('AWAITING_STOCK', 'AWAITING_PAYMENT', 'REJECTED'))
);

CREATE TABLE order_items (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    product_name text NOT NULL,
    unit_price numeric NOT NULL,
    quantity integer NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (
        unit_price NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        AND unit_price >= 0
        AND unit_price = trunc(unit_price, 2)
    )
);

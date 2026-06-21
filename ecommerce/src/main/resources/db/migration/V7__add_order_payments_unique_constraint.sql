-- Add unique constraint for idempotent seeding of order_payments
ALTER TABLE ecommerce.order_payments ADD CONSTRAINT uq_order_payment_seq UNIQUE (order_id, payment_sequential);

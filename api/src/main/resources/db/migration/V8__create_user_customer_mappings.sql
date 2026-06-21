CREATE TABLE ecommerce.user_customer_mappings (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES public.users(id),
    customer_id VARCHAR(64) NOT NULL UNIQUE REFERENCES ecommerce.customers(customer_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ucm_user ON ecommerce.user_customer_mappings(user_id);
CREATE INDEX idx_ucm_customer ON ecommerce.user_customer_mappings(customer_id);

-- Spring AI PgVectorStore table
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,
    metadata JSONB,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- Semantic response cache for L2 pgvector-based lookups
CREATE TABLE IF NOT EXISTS response_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    query_embedding vector(1536),
    query_hash VARCHAR(255) NOT NULL,
    response TEXT NOT NULL,
    query_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_response_cache_tenant ON response_cache(tenant_id);
CREATE INDEX IF NOT EXISTS idx_response_cache_hash ON response_cache(query_hash);
CREATE INDEX IF NOT EXISTS idx_response_cache_expires ON response_cache(expires_at);
CREATE INDEX IF NOT EXISTS idx_response_cache_embedding ON response_cache
    USING hnsw (query_embedding vector_cosine_ops);

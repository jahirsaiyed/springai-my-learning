-- Add observability columns to agent_decisions
ALTER TABLE agent_decisions ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE agent_decisions ADD COLUMN IF NOT EXISTS response_time_ms BIGINT DEFAULT 0;

-- Add indexes for analytics queries
CREATE INDEX IF NOT EXISTS idx_agent_decisions_type ON agent_decisions(agent_type);
CREATE INDEX IF NOT EXISTS idx_token_usage_conv ON token_usage(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversations_created ON conversations(tenant_id, created_at DESC);

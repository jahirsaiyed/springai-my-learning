package com.example.admin.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConversationStats getConversationStats(UUID tenantId) {
        try {
            var row = jdbcTemplate.queryForMap(
                """
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active,
                    COUNT(*) FILTER (WHERE status = 'RESOLVED') AS resolved,
                    COUNT(*) FILTER (WHERE status = 'ESCALATED') AS escalated,
                    COUNT(*) FILTER (WHERE status = 'EXPIRED') AS expired,
                    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours') AS last_24h,
                    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '7 days') AS last_7d
                FROM conversations
                WHERE tenant_id = ?::uuid
                """,
                tenantId.toString()
            );
            return new ConversationStats(
                ((Number) row.get("total")).longValue(),
                ((Number) row.get("active")).longValue(),
                ((Number) row.get("resolved")).longValue(),
                ((Number) row.get("escalated")).longValue(),
                ((Number) row.get("expired")).longValue(),
                ((Number) row.get("last_24h")).longValue(),
                ((Number) row.get("last_7d")).longValue()
            );
        } catch (Exception e) {
            return new ConversationStats(0, 0, 0, 0, 0, 0, 0);
        }
    }

    public TokenUsageStats getTokenUsage(UUID tenantId) {
        try {
            var row = jdbcTemplate.queryForMap(
                """
                SELECT
                    COALESCE(SUM(input_tokens), 0) AS total_input,
                    COALESCE(SUM(output_tokens), 0) AS total_output,
                    COALESCE(SUM(input_tokens + output_tokens), 0) AS total_tokens,
                    COUNT(DISTINCT conversation_id) AS conversations,
                    COALESCE(SUM(input_tokens) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours'), 0) AS input_24h,
                    COALESCE(SUM(output_tokens) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours'), 0) AS output_24h
                FROM token_usage
                WHERE tenant_id = ?::uuid
                """,
                tenantId.toString()
            );
            return new TokenUsageStats(
                ((Number) row.get("total_input")).longValue(),
                ((Number) row.get("total_output")).longValue(),
                ((Number) row.get("total_tokens")).longValue(),
                ((Number) row.get("conversations")).longValue(),
                ((Number) row.get("input_24h")).longValue(),
                ((Number) row.get("output_24h")).longValue()
            );
        } catch (Exception e) {
            return new TokenUsageStats(0, 0, 0, 0, 0, 0);
        }
    }

    public List<AgentUsage> getAgentUsageBreakdown(UUID tenantId) {
        try {
            return jdbcTemplate.query(
                """
                SELECT ad.agent_type, COUNT(*) AS usage_count
                FROM agent_decisions ad
                JOIN conversations c ON ad.conversation_id = c.id
                WHERE c.tenant_id = ?::uuid
                GROUP BY ad.agent_type
                ORDER BY usage_count DESC
                """,
                (rs, rowNum) -> new AgentUsage(
                    rs.getString("agent_type"),
                    rs.getLong("usage_count")
                ),
                tenantId.toString()
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    public KnowledgeBaseStats getKnowledgeBaseStats(UUID tenantId) {
        try {
            var row = jdbcTemplate.queryForMap(
                """
                SELECT
                    COUNT(*) AS total_documents,
                    COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_documents,
                    (SELECT COUNT(*) FROM knowledge_chunks kc
                     JOIN knowledge_documents kd ON kc.document_id = kd.id
                     WHERE kd.tenant_id = ?::uuid) AS total_chunks
                FROM knowledge_documents
                WHERE tenant_id = ?::uuid
                """,
                tenantId.toString(), tenantId.toString()
            );
            return new KnowledgeBaseStats(
                ((Number) row.get("total_documents")).longValue(),
                ((Number) row.get("active_documents")).longValue(),
                ((Number) row.get("total_chunks")).longValue()
            );
        } catch (Exception e) {
            return new KnowledgeBaseStats(0, 0, 0);
        }
    }

    public record ConversationStats(
        long total, long active, long resolved,
        long escalated, long expired, long last24h, long last7d
    ) {}

    public record TokenUsageStats(
        long totalInput, long totalOutput, long totalTokens,
        long conversations, long input24h, long output24h
    ) {}

    public record AgentUsage(String agentType, long usageCount) {}

    public record KnowledgeBaseStats(
        long totalDocuments, long activeDocuments, long totalChunks
    ) {}
}

package com.example.admin.controller;

import com.example.admin.analytics.AnalyticsService;
import com.example.admin.analytics.AnalyticsService.*;
import com.example.core.tenant.TenantContext;
import com.example.memory.cache.CacheMetrics;
import com.example.memory.cache.SemanticCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SemanticCacheService cacheService;

    public AnalyticsController(AnalyticsService analyticsService,
                                SemanticCacheService cacheService) {
        this.analyticsService = analyticsService;
        this.cacheService = cacheService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        var tenant = TenantContext.require();
        var tenantId = tenant.getId();

        var conversations = analyticsService.getConversationStats(tenantId);
        var tokens = analyticsService.getTokenUsage(tenantId);
        var agents = analyticsService.getAgentUsageBreakdown(tenantId);
        var knowledge = analyticsService.getKnowledgeBaseStats(tenantId);
        var cache = cacheService.getStats(tenantId);

        return ResponseEntity.ok(new DashboardResponse(
            conversations, tokens, agents, knowledge, cache
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ConversationStats> conversationStats() {
        var tenant = TenantContext.require();
        return ResponseEntity.ok(analyticsService.getConversationStats(tenant.getId()));
    }

    @GetMapping("/tokens")
    public ResponseEntity<TokenUsageStats> tokenUsage() {
        var tenant = TenantContext.require();
        return ResponseEntity.ok(analyticsService.getTokenUsage(tenant.getId()));
    }

    @GetMapping("/agents")
    public ResponseEntity<List<AgentUsage>> agentUsage() {
        var tenant = TenantContext.require();
        return ResponseEntity.ok(analyticsService.getAgentUsageBreakdown(tenant.getId()));
    }

    @GetMapping("/knowledge")
    public ResponseEntity<KnowledgeBaseStats> knowledgeStats() {
        var tenant = TenantContext.require();
        return ResponseEntity.ok(analyticsService.getKnowledgeBaseStats(tenant.getId()));
    }

    @GetMapping("/cache")
    public ResponseEntity<CacheMetrics.CacheStats> cacheStats() {
        var tenant = TenantContext.require();
        return ResponseEntity.ok(cacheService.getStats(tenant.getId()));
    }

    public record DashboardResponse(
        ConversationStats conversations,
        TokenUsageStats tokens,
        List<AgentUsage> agents,
        KnowledgeBaseStats knowledge,
        CacheMetrics.CacheStats cache
    ) {}
}

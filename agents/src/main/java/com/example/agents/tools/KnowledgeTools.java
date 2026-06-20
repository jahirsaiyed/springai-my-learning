package com.example.agents.tools;

import com.example.memory.MemoryManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Tools for knowledge base and shared insight search.
 * Delegates to the MemoryManager for actual retrieval.
 */
@Component
public class KnowledgeTools {

    private final MemoryManager memoryManager;

    public KnowledgeTools(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    @Tool(description = "Search the knowledge base for relevant articles, FAQs, and product documentation. Use when the customer asks about policies, product details, or general information.")
    public String searchKnowledge(
            @ToolParam(description = "The search query") String query,
            @ToolParam(description = "The tenant ID") String tenantId) {

        UUID tid = UUID.fromString(tenantId);
        List<String> results = memoryManager.semantic().search(tid, query, 3);

        if (results.isEmpty()) {
            return "No relevant knowledge base articles found for: " + query;
        }

        StringBuilder sb = new StringBuilder("Knowledge Base Results:\n\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append("--- Article ").append(i + 1).append(" ---\n");
            sb.append(results.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    @Tool(description = "Search shared insights from previously resolved customer issues. These are lessons learned from past support interactions.")
    public String searchSharedInsights(
            @ToolParam(description = "The search query") String query,
            @ToolParam(description = "The tenant ID") String tenantId) {

        UUID tid = UUID.fromString(tenantId);
        List<String> results = memoryManager.shared().searchInsights(tid, query, 3);

        if (results.isEmpty()) {
            return "No relevant insights found for: " + query;
        }

        StringBuilder sb = new StringBuilder("Insights from Past Resolutions:\n\n");
        for (String insight : results) {
            sb.append("- ").append(insight).append("\n");
        }
        return sb.toString();
    }
}

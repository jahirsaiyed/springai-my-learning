package com.example.memory;

import com.example.memory.episodic.ConversationRecallService;
import com.example.memory.episodic.EpisodicMemoryService;
import com.example.memory.procedural.ProceduralMemoryService;
import com.example.memory.semantic.SemanticMemoryService;
import com.example.memory.shared.SharedMemoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unified coordinator for all 4 memory types.
 * Provides a single entry point for agents to query across memory systems.
 */
@Service
public class MemoryManager {

    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final ProceduralMemoryService proceduralMemory;
    private final SharedMemoryService sharedMemory;
    private final ConversationRecallService recallService;

    public MemoryManager(EpisodicMemoryService episodicMemory,
                          SemanticMemoryService semanticMemory,
                          ProceduralMemoryService proceduralMemory,
                          SharedMemoryService sharedMemory,
                          ConversationRecallService recallService) {
        this.episodicMemory = episodicMemory;
        this.semanticMemory = semanticMemory;
        this.proceduralMemory = proceduralMemory;
        this.sharedMemory = sharedMemory;
        this.recallService = recallService;
    }

    /**
     * Builds a comprehensive context string by querying all relevant memory types.
     * Used by agents to augment their prompts with relevant context.
     */
    public String buildContext(UUID tenantId, UUID customerId, String query) {
        StringBuilder context = new StringBuilder();

        // 1. Episodic: past interactions with this customer
        String recallContext = recallService.buildRecallContext(tenantId, customerId);
        if (!recallContext.isBlank()) {
            context.append("## Customer History\n").append(recallContext).append("\n");
        }

        // 2. Semantic: relevant knowledge base articles (hybrid search: vector + BM25)
        List<String> knowledgeResults = semanticMemory.hybridSearch(tenantId, query, 3);
        if (!knowledgeResults.isEmpty()) {
            context.append("## Relevant Knowledge\n");
            for (String result : knowledgeResults) {
                context.append(result).append("\n---\n");
            }
            context.append("\n");
        }

        // 3. Shared: relevant insights from past resolutions
        List<String> insights = sharedMemory.searchInsights(tenantId, query, 2);
        if (!insights.isEmpty()) {
            context.append("## Insights from Past Resolutions\n");
            for (String insight : insights) {
                context.append("- ").append(insight).append("\n");
            }
            context.append("\n");
        }

        return context.toString();
    }

    /**
     * Searches across all memory types and returns combined results.
     */
    public List<String> searchAll(UUID tenantId, String query, int topK) {
        List<String> results = new ArrayList<>();
        results.addAll(semanticMemory.search(tenantId, query, topK));
        results.addAll(sharedMemory.searchInsights(tenantId, query, topK));
        return results;
    }

    public EpisodicMemoryService episodic() { return episodicMemory; }
    public SemanticMemoryService semantic() { return semanticMemory; }
    public ProceduralMemoryService procedural() { return proceduralMemory; }
    public SharedMemoryService shared() { return sharedMemory; }
    public ConversationRecallService recall() { return recallService; }
}

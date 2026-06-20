package com.example.memory.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultSharedMemoryService implements SharedMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSharedMemoryService.class);
    private static final String INSIGHT_METADATA_TYPE = "shared_insight";

    private final SharedInsightRepository insightRepository;
    private final VectorStore vectorStore;

    public DefaultSharedMemoryService(SharedInsightRepository insightRepository,
                                       VectorStore vectorStore) {
        this.insightRepository = insightRepository;
        this.vectorStore = vectorStore;
    }

    @Override
    @Transactional
    public SharedInsight proposeInsight(UUID tenantId, UUID conversationId, String insight) {
        var sharedInsight = new SharedInsight(tenantId, conversationId, insight);
        sharedInsight = insightRepository.save(sharedInsight);
        log.info("Proposed insight from conversation {} for tenant {}", conversationId, tenantId);
        return sharedInsight;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SharedInsight> getPendingInsights(UUID tenantId) {
        return insightRepository.findByTenantIdAndStatus(tenantId, InsightStatus.PENDING);
    }

    @Override
    @Transactional
    public void approveInsight(UUID insightId, UUID reviewerId) {
        SharedInsight insight = insightRepository.findById(insightId)
            .orElseThrow(() -> new IllegalArgumentException("Insight not found: " + insightId));

        insight.approve(reviewerId);
        insightRepository.save(insight);

        // Add approved insight to vector store for future retrieval
        var vectorDoc = new Document(
            insight.getId().toString(),
            insight.getInsight(),
            Map.of(
                "type", INSIGHT_METADATA_TYPE,
                "tenant_id", insight.getTenantId().toString(),
                "conversation_id", insight.getConversationId() != null
                    ? insight.getConversationId().toString() : "",
                "status", "APPROVED"
            )
        );
        vectorStore.add(List.of(vectorDoc));

        log.info("Approved and indexed insight {}", insightId);
    }

    @Override
    @Transactional
    public void rejectInsight(UUID insightId, UUID reviewerId) {
        SharedInsight insight = insightRepository.findById(insightId)
            .orElseThrow(() -> new IllegalArgumentException("Insight not found: " + insightId));

        insight.reject(reviewerId);
        insightRepository.save(insight);
        log.info("Rejected insight {}", insightId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> searchInsights(UUID tenantId, String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .filterExpression(
                "tenant_id == '" + tenantId + "' && type == '" + INSIGHT_METADATA_TYPE + "'"
            )
            .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream()
            .map(Document::getText)
            .toList();
    }
}

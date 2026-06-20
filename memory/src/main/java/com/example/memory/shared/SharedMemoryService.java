package com.example.memory.shared;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for shared memory operations.
 * Manages cross-customer insights that grow from resolved interactions.
 */
public interface SharedMemoryService {

    SharedInsight proposeInsight(UUID tenantId, UUID conversationId, String insight);

    List<SharedInsight> getPendingInsights(UUID tenantId);

    void approveInsight(UUID insightId, UUID reviewerId);

    void rejectInsight(UUID insightId, UUID reviewerId);

    List<String> searchInsights(UUID tenantId, String query, int topK);
}

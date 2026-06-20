package com.example.memory.episodic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for episodic memory operations.
 * Manages conversation history and cross-session recall.
 */
public interface EpisodicMemoryService {

    Conversation startConversation(UUID tenantId, UUID customerId, Channel channel);

    Message addMessage(UUID conversationId, MessageRole role, String content);

    Optional<Conversation> getConversation(UUID conversationId);

    List<Conversation> getCustomerHistory(UUID tenantId, UUID customerId);

    List<Message> getRecentMessages(UUID conversationId, int limit);

    void closeConversation(UUID conversationId, String summary);
}

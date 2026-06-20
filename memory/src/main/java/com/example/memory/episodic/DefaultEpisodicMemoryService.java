package com.example.memory.episodic;

import com.example.memory.episodic.ConversationSessionCache.CachedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultEpisodicMemoryService implements EpisodicMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEpisodicMemoryService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationSessionCache sessionCache;

    public DefaultEpisodicMemoryService(ConversationRepository conversationRepository,
                                         MessageRepository messageRepository,
                                         ConversationSessionCache sessionCache) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.sessionCache = sessionCache;
    }

    @Override
    @Transactional
    public Conversation startConversation(UUID tenantId, UUID customerId, Channel channel) {
        var conversation = new Conversation(tenantId, customerId, channel);
        conversation = conversationRepository.save(conversation);
        log.info("Started conversation {} for customer {} on channel {}",
            conversation.getId(), customerId, channel);
        return conversation;
    }

    @Override
    @Transactional
    public Message addMessage(UUID conversationId, MessageRole role, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Conversation not found: " + conversationId));

        var message = new Message(role, content);
        conversation.addMessage(message);
        conversationRepository.save(conversation);

        // Cache in Redis for quick session access
        sessionCache.cacheMessage(conversationId, new CachedMessage(
            role.name(), content, Instant.now().toEpochMilli()
        ));

        return message;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> getCustomerHistory(UUID tenantId, UUID customerId) {
        return conversationRepository
            .findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> getRecentMessages(UUID conversationId, int limit) {
        // Try Redis cache first
        List<CachedMessage> cached = sessionCache.getSessionMessages(conversationId);
        if (!cached.isEmpty()) {
            log.debug("Returning {} cached messages for conversation {}", cached.size(), conversationId);
            return cached.stream()
                .map(cm -> new Message(MessageRole.valueOf(cm.role()), cm.content()))
                .toList();
        }

        // Fall back to DB
        return messageRepository.findRecentMessages(conversationId, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void closeConversation(UUID conversationId, String summary) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Conversation not found: " + conversationId));

        conversation.setStatus(ConversationStatus.RESOLVED);
        conversation.setSummary(summary);
        conversationRepository.save(conversation);

        sessionCache.evict(conversationId);
        log.info("Closed conversation {}", conversationId);
    }
}

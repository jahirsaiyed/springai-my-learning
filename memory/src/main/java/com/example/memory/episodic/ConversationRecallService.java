package com.example.memory.episodic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates conversation summaries and provides cross-session recall.
 * Uses the LLM to summarize conversations and find relevant past interactions.
 */
@Service
public class ConversationRecallService {

    private static final Logger log = LoggerFactory.getLogger(ConversationRecallService.class);
    private static final int MAX_HISTORY_CONVERSATIONS = 5;

    private final ConversationRepository conversationRepository;
    private final ChatClient.Builder chatClientBuilder;

    public ConversationRecallService(ConversationRepository conversationRepository,
                                      ChatClient.Builder chatClientBuilder) {
        this.conversationRepository = conversationRepository;
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * Generates a summary for a conversation using the LLM.
     */
    public String generateSummary(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        if (messages.isEmpty()) {
            return "Empty conversation";
        }

        String transcript = messages.stream()
            .map(m -> m.getRole().name() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));

        ChatClient client = chatClientBuilder
            .defaultSystem("""
                You are a conversation summarizer. Create a brief 2-3 sentence summary
                of the customer support conversation. Focus on: the customer's issue,
                the resolution (if any), and any follow-up actions needed.
                """)
            .build();

        return client.prompt()
            .user(transcript)
            .call()
            .content();
    }

    /**
     * Retrieves relevant past conversations for a customer to provide context.
     * Returns summaries of recent interactions.
     */
    public List<String> recallPastInteractions(UUID tenantId, UUID customerId) {
        List<Conversation> history = conversationRepository
            .findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId);

        return history.stream()
            .limit(MAX_HISTORY_CONVERSATIONS)
            .filter(c -> c.getSummary() != null && !c.getSummary().isBlank())
            .map(c -> "[" + c.getCreatedAt().toString().substring(0, 10) + " via " + c.getChannel()
                + "] " + c.getSummary())
            .toList();
    }

    /**
     * Builds a context string from past interactions to inject into the agent prompt.
     */
    public String buildRecallContext(UUID tenantId, UUID customerId) {
        List<String> pastInteractions = recallPastInteractions(tenantId, customerId);
        if (pastInteractions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Previous interactions with this customer:\n");
        for (String interaction : pastInteractions) {
            sb.append("- ").append(interaction).append("\n");
        }
        return sb.toString();
    }
}

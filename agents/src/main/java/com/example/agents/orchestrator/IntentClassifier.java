package com.example.agents.orchestrator;

import com.example.agents.AgentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Classifies user intent to route to the appropriate subagent.
 * Uses LLM for nuanced understanding with keyword fallback.
 */
@Component
public class IntentClassifier {

    private final ChatClient classifierClient;

    public IntentClassifier(ChatClient.Builder chatClientBuilder) {
        this.classifierClient = chatClientBuilder
            .defaultSystem("""
                You are an intent classifier for a customer support system.
                Classify the customer's message into exactly ONE of these categories:

                ORDER - Order lookup, tracking, shipment status, delivery questions, order cancellation
                REFUND - Refund requests, return requests, money back, overcharges, refund status
                KNOWLEDGE - Policy questions, FAQ, product information, general "how to" questions
                ESCALATION - Explicit request for human agent, complaints, unresolved frustration

                Respond with ONLY the category name (ORDER, REFUND, KNOWLEDGE, or ESCALATION).
                Nothing else.
                """)
            .build();
    }

    public IntentClassification classify(String userMessage) {
        // Quick keyword-based classification for common patterns
        IntentClassification keywordResult = keywordClassify(userMessage);
        if (keywordResult.isHighConfidence()) {
            return keywordResult;
        }

        // Fall back to LLM classification for ambiguous messages
        try {
            String result = classifierClient.prompt()
                .user(userMessage)
                .call()
                .content();

            AgentType agentType = parseAgentType(result.trim().toUpperCase());
            return new IntentClassification(agentType, 0.85, "LLM classification: " + result);
        } catch (Exception e) {
            // Default to knowledge agent on classification failure
            return new IntentClassification(AgentType.KNOWLEDGE, 0.5,
                "Classification failed, defaulting to knowledge");
        }
    }

    private IntentClassification keywordClassify(String message) {
        String lower = message.toLowerCase();

        if (containsAny(lower, "track", "where is my order", "shipping status",
                "delivery", "when will", "order status", "my order")) {
            return new IntentClassification(AgentType.ORDER, 0.9, "Keyword: order/tracking");
        }
        if (containsAny(lower, "cancel my order", "cancel order", "cancellation")) {
            return new IntentClassification(AgentType.ORDER, 0.9, "Keyword: cancellation");
        }
        if (containsAny(lower, "refund", "return", "money back", "reimburse",
                "credit back", "overcharged")) {
            return new IntentClassification(AgentType.REFUND, 0.9, "Keyword: refund/return");
        }
        if (containsAny(lower, "speak to human", "talk to agent", "real person",
                "manager", "supervisor", "complaint", "frustrated", "unacceptable")) {
            return new IntentClassification(AgentType.ESCALATION, 0.9, "Keyword: escalation");
        }
        if (containsAny(lower, "policy", "warranty", "terms", "how does",
                "what is your", "do you offer")) {
            return new IntentClassification(AgentType.KNOWLEDGE, 0.8, "Keyword: policy/FAQ");
        }

        return new IntentClassification(AgentType.KNOWLEDGE, 0.4, "No keyword match");
    }

    private AgentType parseAgentType(String text) {
        if (text.contains("ORDER")) return AgentType.ORDER;
        if (text.contains("REFUND")) return AgentType.REFUND;
        if (text.contains("ESCALATION")) return AgentType.ESCALATION;
        return AgentType.KNOWLEDGE;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}

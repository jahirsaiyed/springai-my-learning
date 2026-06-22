package com.example.agents.orchestrator;

import com.example.agents.AgentType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Classifies user intent to route to the appropriate subagent.
 * Uses keyword fast-path then LLM fallback with conversation history context.
 */
@Component
public class IntentClassifier {

    private static final int MAX_HISTORY_MESSAGES = 6;

    private static final String SYSTEM_PROMPT = """
        You are an intent classifier for a customer support system.
        Given the conversation history and the latest customer message,
        classify the intent into exactly ONE category:

        ORDER - Order lookup, tracking, shipment status, delivery questions, order cancellation
        REFUND - Refund requests, return requests, money back, overcharges, refund status
        KNOWLEDGE - Policy questions, FAQ, product information, general inquiries
        ESCALATION - Explicit request for human agent, complaints, unresolved frustration

        Respond with ONLY the category name (ORDER, REFUND, KNOWLEDGE, or ESCALATION).
        Nothing else.
        """;

    private final ChatModel chatModel;

    public IntentClassifier(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public IntentClassification classify(String userMessage) {
        return classify(userMessage, List.of());
    }

    public IntentClassification classify(String userMessage, List<Message> conversationHistory) {
        // Quick keyword-based classification for common patterns
        IntentClassification keywordResult = keywordClassify(userMessage);
        if (keywordResult.isHighConfidence()) {
            return keywordResult;
        }

        // Fall back to LLM classification with conversation context for ambiguous messages
        try {
            String classificationPrompt = buildClassificationPrompt(userMessage, conversationHistory);
            Prompt prompt = new Prompt(List.of(
                new org.springframework.ai.chat.messages.SystemMessage(SYSTEM_PROMPT),
                new UserMessage(classificationPrompt)
            ));
            String result = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText()
                .trim()
                .toUpperCase();

            AgentType agentType = parseAgentType(result);
            return new IntentClassification(agentType, 0.85, "LLM classification: " + result);
        } catch (Exception e) {
            // Default to knowledge agent on classification failure
            return new IntentClassification(AgentType.KNOWLEDGE, 0.5,
                "Classification failed, defaulting to knowledge");
        }
    }

    private String buildClassificationPrompt(String userMessage, List<Message> history) {
        StringBuilder sb = new StringBuilder();

        if (!history.isEmpty()) {
            sb.append("CONVERSATION HISTORY:\n");
            int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            for (int i = start; i < history.size(); i++) {
                Message msg = history.get(i);
                String role = (msg instanceof AssistantMessage) ? "AGENT" : "CUSTOMER";
                sb.append(role).append(": ").append(msg.getText()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("LATEST MESSAGE:\n").append(userMessage);
        return sb.toString();
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

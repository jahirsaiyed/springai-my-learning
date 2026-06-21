package com.example.agents.subagent;

import com.example.agents.AgentContext;
import com.example.agents.AgentResponse;
import com.example.agents.AgentType;
import com.example.agents.tools.RefundTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

// @Component — disabled: replaced by LangGraph4j SupportGraph
public class RefundAgent implements SubAgent {

    private final ChatClient chatClient;

    public RefundAgent(ChatClient.Builder chatClientBuilder, RefundTools refundTools) {
        this.chatClient = chatClientBuilder
            .defaultSystem("""
                You are a Refund Support Agent for an e-commerce platform.
                Your responsibilities:
                - Check if orders are eligible for refunds
                - Process refund requests
                - Check refund status
                - Explain return/refund policies

                Guidelines:
                - ALWAYS check refund eligibility before processing
                - ALWAYS confirm the refund amount and method with the customer before initiating
                - Explain the refund timeline clearly
                - If the order is not eligible, explain why and offer alternatives
                - For partial refunds, clearly state what is being refunded
                - Never process a refund without explicit customer confirmation
                """)
            .defaultTools(refundTools)
            .build();
    }

    @Override
    public AgentType getType() {
        return AgentType.REFUND;
    }

    @Override
    public AgentResponse handle(String userMessage, AgentContext context) {
        String prompt = buildPrompt(userMessage, context);

        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        boolean needsConfirmation = response.toLowerCase().contains("confirm")
            || response.toLowerCase().contains("would you like to proceed")
            || response.toLowerCase().contains("shall i process");

        return needsConfirmation
            ? AgentResponse.withConfirmation(response, AgentType.REFUND, Map.of())
            : AgentResponse.of(response, AgentType.REFUND);
    }

    private String buildPrompt(String userMessage, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        if (!context.memoryContext().isBlank()) {
            prompt.append("Context from memory:\n").append(context.memoryContext()).append("\n\n");
        }
        appendConversationHistory(prompt, context);
        prompt.append("Customer ID: ").append(context.customerId()).append("\n");
        prompt.append("Customer message: ").append(userMessage);
        return prompt.toString();
    }

    private void appendConversationHistory(StringBuilder prompt, AgentContext context) {
        if (context.conversationHistory() != null && !context.conversationHistory().isEmpty()) {
            prompt.append("## Conversation History\n");
            for (var msg : context.conversationHistory()) {
                prompt.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
            prompt.append("\n");
        }
    }
}

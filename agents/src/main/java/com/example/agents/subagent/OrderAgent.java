package com.example.agents.subagent;

import com.example.agents.AgentContext;
import com.example.agents.AgentResponse;
import com.example.agents.AgentType;
import com.example.agents.tools.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * @deprecated Replaced by SupportGraph order_agent node. Kept for reference.
 */
// @Component — disabled: replaced by LangGraph4j SupportGraph
public class OrderAgent implements SubAgent {

    private final ChatClient chatClient;

    public OrderAgent(ChatClient.Builder chatClientBuilder, OrderTools orderTools) {
        this.chatClient = chatClientBuilder
            .defaultSystem("""
                You are an Order Support Agent for an e-commerce platform.
                Your responsibilities:
                - Look up order details by order ID
                - Track shipments and provide delivery estimates
                - Cancel orders (only if not yet shipped)
                - Show recent orders for a customer

                Guidelines:
                - Always verify the order exists before taking action
                - For cancellations, ALWAYS confirm with the customer before proceeding
                - Be precise with dates, amounts, and tracking information
                - If you cannot resolve the issue, suggest escalation to a human agent
                - Never make up order information — use the tools to look up real data
                """)
            .defaultTools(orderTools)
            .build();
    }

    @Override
    public AgentType getType() {
        return AgentType.ORDER;
    }

    @Override
    public AgentResponse handle(String userMessage, AgentContext context) {
        String prompt = buildPrompt(userMessage, context);

        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        boolean needsConfirmation = response.toLowerCase().contains("would you like to proceed")
            || response.toLowerCase().contains("please confirm");

        return needsConfirmation
            ? AgentResponse.withConfirmation(response, AgentType.ORDER, java.util.Map.of())
            : AgentResponse.of(response, AgentType.ORDER);
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

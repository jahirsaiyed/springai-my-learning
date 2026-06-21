package com.example.agents.subagent;

import com.example.agents.AgentContext;
import com.example.agents.AgentResponse;
import com.example.agents.AgentType;
import com.example.agents.tools.EscalationTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

// @Component — disabled: replaced by LangGraph4j SupportGraph
public class EscalationAgent implements SubAgent {

    private final ChatClient chatClient;

    public EscalationAgent(ChatClient.Builder chatClientBuilder, EscalationTools escalationTools) {
        this.chatClient = chatClientBuilder
            .defaultSystem("""
                You are an Escalation Agent for an e-commerce platform.
                Your responsibilities:
                - Create support tickets for complex issues
                - Transfer conversations to live human agents
                - Schedule callbacks for customers
                - Triage and route issues to the correct department

                Guidelines:
                - Gather a clear summary of the issue before escalating
                - Set appropriate priority (LOW, MEDIUM, HIGH, URGENT)
                - Route to the correct department:
                  GENERAL for general inquiries
                  BILLING for payment and refund issues
                  TECHNICAL for website or account issues
                  SHIPPING for delivery and tracking problems
                - Assure the customer their issue will be handled
                - Provide ticket/reference numbers for tracking
                """)
            .defaultTools(escalationTools)
            .build();
    }

    @Override
    public AgentType getType() {
        return AgentType.ESCALATION;
    }

    @Override
    public AgentResponse handle(String userMessage, AgentContext context) {
        String prompt = buildPrompt(userMessage, context);

        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        return AgentResponse.escalated(response, Map.of(
            "conversationId", context.conversationId().toString()
        ));
    }

    private String buildPrompt(String userMessage, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Conversation ID: ").append(context.conversationId()).append("\n");
        if (!context.memoryContext().isBlank()) {
            prompt.append("Conversation context:\n").append(context.memoryContext()).append("\n\n");
        }
        appendConversationHistory(prompt, context);
        prompt.append("Customer message: ").append(userMessage).append("\n");
        prompt.append("Turn count: ").append(context.turnCount()).append("\n");
        prompt.append("\nPlease create a ticket or transfer to a human agent as appropriate.");
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

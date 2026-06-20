package com.example.agents.subagent;

import com.example.agents.AgentContext;
import com.example.agents.AgentResponse;
import com.example.agents.AgentType;
import com.example.agents.tools.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeAgent implements SubAgent {

    private final ChatClient chatClient;

    public KnowledgeAgent(ChatClient.Builder chatClientBuilder, KnowledgeTools knowledgeTools) {
        this.chatClient = chatClientBuilder
            .defaultSystem("""
                You are a Knowledge Support Agent for an e-commerce platform.
                Your responsibilities:
                - Answer questions about policies (return, refund, shipping, warranty)
                - Provide product information from the knowledge base
                - Answer FAQs
                - Share relevant insights from past customer resolutions

                Guidelines:
                - Always search the knowledge base before answering
                - Quote relevant policy sections when applicable
                - If no relevant information is found, say so honestly
                - Do not make up information — only use what the tools provide
                - If the question requires action (not just information), suggest
                  the customer ask about that specific action
                """)
            .defaultTools(knowledgeTools)
            .build();
    }

    @Override
    public AgentType getType() {
        return AgentType.KNOWLEDGE;
    }

    @Override
    public AgentResponse handle(String userMessage, AgentContext context) {
        String prompt = buildPrompt(userMessage, context);

        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        return AgentResponse.of(response, AgentType.KNOWLEDGE);
    }

    private String buildPrompt(String userMessage, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Tenant ID: ").append(context.tenantId()).append("\n");
        if (!context.memoryContext().isBlank()) {
            prompt.append("Context from memory:\n").append(context.memoryContext()).append("\n\n");
        }
        prompt.append("Customer question: ").append(userMessage);
        return prompt.toString();
    }
}

package com.example.agents.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AskUserQuestion pattern from Spring AI agentic patterns.
 * When the agent needs clarification, it uses this tool to formulate
 * a clear question back to the user rather than guessing.
 */
@Component
public class AskUserQuestionTool {

    @Tool(description = """
        Use this tool when you need more information from the customer to proceed.
        Instead of guessing or making assumptions, ask a clear, specific question.
        Examples of when to use:
        - Customer mentions "my order" but doesn't provide an order ID
        - Refund request but unclear which item to refund
        - Ambiguous request that could mean multiple things
        The question will be sent directly to the customer.
        """)
    public String askUserQuestion(
            @ToolParam(description = "The specific question to ask the customer") String question) {
        // This tool returns the question as-is. The orchestrator intercepts this
        // and sends it to the user as a clarification request.
        return "CLARIFICATION_NEEDED: " + question;
    }
}

package com.example.agents.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Tools for escalation to human agents.
 * Mock implementations — designed for integration with ticketing systems
 * (Zendesk, Jira Service Desk, Freshdesk, etc.).
 */
@Component
public class EscalationTools {

    @Tool(description = "Create a support ticket for human agent review. Use when the issue cannot be resolved automatically.")
    public String createTicket(
            @ToolParam(description = "Brief summary of the issue") String summary,
            @ToolParam(description = "Priority: LOW, MEDIUM, HIGH, URGENT") String priority,
            @ToolParam(description = "Detailed description including conversation context") String description) {

        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "Support Ticket Created:\n"
            + "- Ticket ID: " + ticketId + "\n"
            + "- Summary: " + summary + "\n"
            + "- Priority: " + priority + "\n"
            + "- Status: OPEN\n"
            + "- Created: " + Instant.now() + "\n"
            + "- A support agent will review your case shortly.";
    }

    @Tool(description = "Transfer the conversation to a live human agent. Use for complex issues or when the customer requests human assistance.")
    public String transferToHuman(
            @ToolParam(description = "Reason for transfer") String reason,
            @ToolParam(description = "Department: GENERAL, BILLING, TECHNICAL, SHIPPING") String department) {

        return "Transfer Initiated:\n"
            + "- Department: " + department + "\n"
            + "- Reason: " + reason + "\n"
            + "- Estimated Wait Time: 3-5 minutes\n"
            + "- Your conversation history has been forwarded to the agent.\n"
            + "- A human agent will be with you shortly.";
    }

    @Tool(description = "Schedule a callback from a support agent at a specified time.")
    public String scheduleCallback(
            @ToolParam(description = "Preferred callback date and time") String preferredTime,
            @ToolParam(description = "Brief description of the issue") String issue) {

        String callbackId = "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "Callback Scheduled:\n"
            + "- Callback ID: " + callbackId + "\n"
            + "- Preferred Time: " + preferredTime + "\n"
            + "- Issue: " + issue + "\n"
            + "- Status: SCHEDULED\n"
            + "- A support agent will call you at the requested time.";
    }
}

package com.example.agents.graph;

import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.Message;

import java.util.*;

/**
 * State for the support agent graph. Extends MessagesState to get
 * automatic message accumulation across turns via the "messages" appender channel.
 */
public class SupportGraphState extends MessagesState<Message> {

    public static final String TENANT_ID = "tenantId";
    public static final String CUSTOMER_ID = "customerId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String MEMORY_CONTEXT = "memoryContext";
    public static final String INTENT = "intent";
    public static final String CACHED_RESPONSE = "cachedResponse";
    public static final String TURN_COUNT = "turnCount";
    public static final String GUARDRAIL_RESULT = "guardrailResult";
    public static final String RESPONSE_TEXT = "responseText";
    public static final String HANDLED_BY = "handledBy";
    public static final String ECOM_CUSTOMER_ID = "ecomCustomerId";
    public static final String MCP_FAILURE_COUNT = "mcpFailureCount";

    public static final Map<String, Channel<?>> SCHEMA;

    static {
        var schema = new HashMap<>(MessagesState.SCHEMA);
        // All other fields use default "last writer wins" (no special channel needed)
        SCHEMA = Collections.unmodifiableMap(schema);
    }

    public SupportGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public UUID tenantId() {
        return this.<UUID>value(TENANT_ID).orElseThrow();
    }

    public UUID customerId() {
        return this.<UUID>value(CUSTOMER_ID).orElseThrow();
    }

    public String ecomCustomerId() {
        return this.<String>value(ECOM_CUSTOMER_ID).orElse(null);
    }

    public UUID conversationId() {
        return this.<UUID>value(CONVERSATION_ID).orElseThrow();
    }

    public String memoryContext() {
        return this.<String>value(MEMORY_CONTEXT).orElse("");
    }

    public String intent() {
        return this.<String>value(INTENT).orElse("KNOWLEDGE");
    }

    public Optional<String> cachedResponse() {
        return this.value(CACHED_RESPONSE);
    }

    public int turnCount() {
        return this.<Integer>value(TURN_COUNT).orElse(1);
    }

    public String guardrailResult() {
        return this.<String>value(GUARDRAIL_RESULT).orElse("pass");
    }

    public String responseText() {
        return this.<String>value(RESPONSE_TEXT).orElse("");
    }

    public String handledBy() {
        return this.<String>value(HANDLED_BY).orElse("ORCHESTRATOR");
    }

    public int mcpFailureCount() {
        return this.<Integer>value(MCP_FAILURE_COUNT).orElse(0);
    }
}

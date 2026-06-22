package com.example.agents.orchestrator;

import com.example.agents.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntentClassifierTest {

    @Mock
    private ChatModel chatModel;

    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new IntentClassifier(chatModel);
    }

    @Test
    @DisplayName("keyword match 'track my order' returns ORDER with high confidence")
    void classify_trackMyOrder_returnsOrderHighConfidence() {
        IntentClassification result = classifier.classify("track my order");

        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
        assertThat(result.isHighConfidence()).isTrue();
    }

    @Test
    @DisplayName("keyword match 'I want a refund' returns REFUND with high confidence")
    void classify_wantRefund_returnsRefundHighConfidence() {
        IntentClassification result = classifier.classify("I want a refund");

        assertThat(result.targetAgent()).isEqualTo(AgentType.REFUND);
        assertThat(result.isHighConfidence()).isTrue();
    }

    @Test
    @DisplayName("keyword match 'I want to return this' returns REFUND with high confidence")
    void classify_wantReturn_returnsRefundHighConfidence() {
        IntentClassification result = classifier.classify("I want to return this");

        assertThat(result.targetAgent()).isEqualTo(AgentType.REFUND);
        assertThat(result.isHighConfidence()).isTrue();
    }

    @Test
    @DisplayName("bare order ID with no history falls back to LLM and returns ORDER")
    void classify_bareOrderIdNoHistory_callsLlmReturnsOrder() {
        var generation = new Generation(new AssistantMessage("ORDER"));
        var chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        IntentClassification result = classifier.classify("ORD-98765", List.of());

        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
        assertThat(result.isHighConfidence()).isTrue();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("bare order ID with order conversation history passes context to LLM")
    void classify_bareOrderIdWithHistory_includesContextInPrompt() {
        var generation = new Generation(new AssistantMessage("ORDER"));
        var chatResponse = new ChatResponse(List.of(generation));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        List<Message> history = List.of(
            new UserMessage("I placed an order yesterday"),
            new AssistantMessage("I can help you with your order. What is your order ID?")
        );

        IntentClassification result = classifier.classify("ORD-98765", history);

        verify(chatModel).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();
        // The prompt messages should include conversation context
        String promptContent = capturedPrompt.getContents();
        assertThat(promptContent).contains("CONVERSATION HISTORY");
        assertThat(promptContent).contains("LATEST MESSAGE");
        assertThat(result.targetAgent()).isEqualTo(AgentType.ORDER);
    }

    @Test
    @DisplayName("single-arg classify delegates to two-arg with empty history")
    void classify_singleArg_delegatesToTwoArgWithEmptyHistory() {
        var generation = new Generation(new AssistantMessage("KNOWLEDGE"));
        var chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // "xyz123" has no keyword match, so it goes to LLM
        IntentClassification result = classifier.classify("xyz123");

        assertThat(result.targetAgent()).isEqualTo(AgentType.KNOWLEDGE);
        verify(chatModel).call(any(Prompt.class));
    }
}

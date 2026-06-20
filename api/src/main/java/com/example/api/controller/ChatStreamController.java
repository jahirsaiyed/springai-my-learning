package com.example.api.controller;

import com.example.agents.orchestrator.StreamingOrchestratorAgent;
import com.example.agents.orchestrator.StreamingOrchestratorAgent.StreamEvent;
import com.example.api.security.AuthenticatedUser;
import com.example.core.tenant.TenantContext;
import com.example.memory.episodic.Channel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * SSE (Server-Sent Events) streaming endpoint for real-time chat.
 * Clients connect via EventSource and receive tokens as they're generated.
 *
 * Usage:
 *   POST /api/chat/stream/start  -> SSE stream of tokens
 *   POST /api/chat/stream/{id}   -> SSE stream of tokens for existing conversation
 */
@RestController
@RequestMapping("/api/chat/stream")
public class ChatStreamController {

    private final StreamingOrchestratorAgent streamingOrchestrator;

    public ChatStreamController(StreamingOrchestratorAgent streamingOrchestrator) {
        this.streamingOrchestrator = streamingOrchestrator;
    }

    @PostMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> startConversationStream(
            @Valid @RequestBody StartStreamRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        var tenant = TenantContext.require();
        Channel channel = request.channel() != null
            ? Channel.valueOf(request.channel().toUpperCase())
            : Channel.WEB;

        return streamingOrchestrator.startConversationStream(
            tenant.getId(), user.id(), channel, request.message()
        ).onErrorResume(e -> Flux.just(StreamEvent.error(e.getMessage())));
    }

    @PostMapping(value = "/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> continueConversationStream(
            @PathVariable UUID conversationId,
            @Valid @RequestBody StreamMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        var tenant = TenantContext.require();

        return streamingOrchestrator.continueConversationStream(
            tenant.getId(), user.id(), conversationId, request.message()
        ).onErrorResume(e -> Flux.just(StreamEvent.error(e.getMessage())));
    }

    public record StartStreamRequest(
        @NotBlank String message,
        String channel
    ) {}

    public record StreamMessageRequest(
        @NotBlank String message
    ) {}
}

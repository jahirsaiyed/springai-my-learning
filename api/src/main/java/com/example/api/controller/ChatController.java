package com.example.api.controller;

import com.example.agents.AgentResponse;
import com.example.agents.orchestrator.OrchestratorAgent;
import com.example.core.tenant.TenantContext;
import com.example.api.security.AuthenticatedUser;
import com.example.ecommerce.service.CustomerResolver;
import com.example.memory.episodic.Channel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OrchestratorAgent orchestrator;
    private final CustomerResolver customerResolver;

    public ChatController(OrchestratorAgent orchestrator, CustomerResolver customerResolver) {
        this.orchestrator = orchestrator;
        this.customerResolver = customerResolver;
    }

    @PostMapping("/start")
    public ResponseEntity<ChatResponse> startConversation(
            @Valid @RequestBody StartChatRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        var tenant = TenantContext.require();
        Channel channel = request.channel() != null
            ? Channel.valueOf(request.channel().toUpperCase())
            : Channel.WEB;

        String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);
        AgentResponse response = orchestrator.startConversation(
            tenant.getId(), user.id(), channel, request.message(), ecomCustomerId);

        return ResponseEntity.ok(ChatResponse.from(response));
    }

    @PostMapping("/{conversationId}")
    public ResponseEntity<ChatResponse> continueConversation(
            @PathVariable UUID conversationId,
            @Valid @RequestBody MessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        var tenant = TenantContext.require();

        String ecomCustomerId = customerResolver.resolve(user.id()).orElse(null);
        AgentResponse response = orchestrator.continueConversation(
            tenant.getId(), user.id(), conversationId, request.message(), ecomCustomerId);

        return ResponseEntity.ok(ChatResponse.from(response));
    }

    @PostMapping("/{conversationId}/resolve")
    public ResponseEntity<Void> resolveConversation(
            @PathVariable UUID conversationId,
            @RequestBody(required = false) ResolveRequest request) {

        var tenant = TenantContext.require();
        String resolution = request != null ? request.resolution() : null;

        orchestrator.resolveConversation(tenant.getId(), conversationId, resolution);
        return ResponseEntity.noContent().build();
    }

    public record StartChatRequest(
        @NotBlank String message,
        String channel
    ) {}

    public record MessageRequest(
        @NotBlank String message
    ) {}

    public record ResolveRequest(String resolution) {}

    public record ChatResponse(
        String conversationId,
        String message,
        String handledBy,
        boolean requiresConfirmation,
        boolean escalated
    ) {
        public static ChatResponse from(AgentResponse response) {
            String convId = response.metadata() != null
                ? (String) response.metadata().get("conversationId")
                : null;
            return new ChatResponse(
                convId,
                response.message(),
                response.handledBy().name(),
                response.requiresConfirmation(),
                response.escalated()
            );
        }
    }
}

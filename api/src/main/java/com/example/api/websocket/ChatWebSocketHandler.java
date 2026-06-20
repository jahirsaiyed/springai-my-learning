package com.example.api.websocket;

import com.example.agents.AgentResponse;
import com.example.agents.orchestrator.OrchestratorAgent;
import com.example.agents.orchestrator.StreamingOrchestratorAgent;
import com.example.agents.orchestrator.StreamingOrchestratorAgent.StreamEvent;
import com.example.api.security.AuthenticatedUser;
import com.example.core.tenant.Tenant;
import com.example.core.tenant.TenantContext;
import com.example.core.tenant.TenantRepository;
import com.example.memory.episodic.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * WebSocket message handler for real-time chat via STOMP.
 *
 * Client sends:
 *   /app/chat.start       -> starts new conversation
 *   /app/chat.send/{id}   -> sends message to existing conversation
 *
 * Server publishes to:
 *   /user/queue/chat.stream -> streaming tokens
 *   /user/queue/chat.response -> complete response (non-streaming fallback)
 */
@Controller
public class ChatWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final OrchestratorAgent orchestrator;
    private final StreamingOrchestratorAgent streamingOrchestrator;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantRepository tenantRepository;

    public ChatWebSocketHandler(OrchestratorAgent orchestrator,
                                 StreamingOrchestratorAgent streamingOrchestrator,
                                 SimpMessagingTemplate messagingTemplate,
                                 TenantRepository tenantRepository) {
        this.orchestrator = orchestrator;
        this.streamingOrchestrator = streamingOrchestrator;
        this.messagingTemplate = messagingTemplate;
        this.tenantRepository = tenantRepository;
    }

    @MessageMapping("/chat.start")
    public void startConversation(@Payload WsStartRequest request, Principal principal) {
        AuthenticatedUser user = extractUser(principal);
        if (user == null) return;

        Tenant tenant = resolveTenant(request.tenantSlug());
        if (tenant == null) {
            sendToUser(principal, "/queue/chat.error", new WsError("Invalid tenant"));
            return;
        }

        try {
            TenantContext.set(tenant);
            Channel channel = Channel.WEBSOCKET;

            // Stream response token by token
            streamingOrchestrator.startConversationStream(
                tenant.getId(), user.id(), channel, request.message()
            ).subscribe(
                event -> sendToUser(principal, "/queue/chat.stream", event),
                error -> {
                    log.error("Streaming error", error);
                    sendToUser(principal, "/queue/chat.error",
                        new WsError("Processing failed"));
                }
            );
        } finally {
            TenantContext.clear();
        }
    }

    @MessageMapping("/chat.send/{conversationId}")
    public void sendMessage(@DestinationVariable UUID conversationId,
                             @Payload WsMessageRequest request,
                             Principal principal) {
        AuthenticatedUser user = extractUser(principal);
        if (user == null) return;

        Tenant tenant = resolveTenant(request.tenantSlug());
        if (tenant == null) {
            sendToUser(principal, "/queue/chat.error", new WsError("Invalid tenant"));
            return;
        }

        try {
            TenantContext.set(tenant);

            streamingOrchestrator.continueConversationStream(
                tenant.getId(), user.id(), conversationId, request.message()
            ).subscribe(
                event -> sendToUser(principal, "/queue/chat.stream", event),
                error -> {
                    log.error("Streaming error for conversation {}", conversationId, error);
                    sendToUser(principal, "/queue/chat.error",
                        new WsError("Processing failed"));
                }
            );
        } finally {
            TenantContext.clear();
        }
    }

    @MessageMapping("/chat.resolve/{conversationId}")
    public void resolveConversation(@DestinationVariable UUID conversationId,
                                     @Payload WsResolveRequest request,
                                     Principal principal) {
        AuthenticatedUser user = extractUser(principal);
        if (user == null) return;

        Tenant tenant = resolveTenant(request.tenantSlug());
        if (tenant == null) return;

        try {
            TenantContext.set(tenant);
            orchestrator.resolveConversation(tenant.getId(), conversationId, request.resolution());
            sendToUser(principal, "/queue/chat.resolved",
                new WsResolved(conversationId.toString()));
        } finally {
            TenantContext.clear();
        }
    }

    private AuthenticatedUser extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return (AuthenticatedUser) auth.getPrincipal();
        }
        return null;
    }

    private Tenant resolveTenant(String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) return null;
        return tenantRepository.findBySlug(tenantSlug).orElse(null);
    }

    private void sendToUser(Principal principal, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(
            principal.getName(), destination, payload);
    }

    public record WsStartRequest(String message, String tenantSlug) {}
    public record WsMessageRequest(String message, String tenantSlug) {}
    public record WsResolveRequest(String resolution, String tenantSlug) {}
    public record WsError(String error) {}
    public record WsResolved(String conversationId) {}
}

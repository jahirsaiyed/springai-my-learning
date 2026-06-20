package com.example.admin.controller;

import com.example.agents.observability.AgentDecisionTracker;
import com.example.core.tenant.TenantContext;
import com.example.memory.episodic.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/conversations")
public class ConversationAdminController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentDecisionTracker decisionTracker;

    public ConversationAdminController(ConversationRepository conversationRepository,
                                        MessageRepository messageRepository,
                                        AgentDecisionTracker decisionTracker) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.decisionTracker = decisionTracker;
    }

    @GetMapping
    public ResponseEntity<List<ConversationSummary>> list(
            @RequestParam(required = false) String status) {
        var tenant = TenantContext.require();
        List<Conversation> conversations;
        if (status != null) {
            conversations = conversationRepository.findByTenantIdAndStatus(
                tenant.getId(), ConversationStatus.valueOf(status.toUpperCase()));
        } else {
            conversations = conversationRepository.findByTenantIdAndStatus(
                tenant.getId(), ConversationStatus.ACTIVE);
        }
        return ResponseEntity.ok(conversations.stream().map(ConversationSummary::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDetail> get(@PathVariable UUID id) {
        return conversationRepository.findById(id)
            .map(c -> {
                List<MessageResponse> messages = c.getMessages().stream()
                    .map(MessageResponse::from)
                    .toList();
                return ResponseEntity.ok(new ConversationDetail(
                    c.getId(), c.getCustomerId(), c.getChannel().name(),
                    c.getStatus().name(), c.getSummary(),
                    c.getCreatedAt(), c.getUpdatedAt(), messages
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/decisions")
    public ResponseEntity<List<Map<String, Object>>> getDecisions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(decisionTracker.getRecentDecisions(id, limit));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ConversationSummary>> listByCustomer(@PathVariable UUID customerId) {
        var tenant = TenantContext.require();
        List<Conversation> conversations = conversationRepository
            .findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenant.getId(), customerId);
        return ResponseEntity.ok(conversations.stream().map(ConversationSummary::from).toList());
    }

    public record ConversationSummary(
        UUID id, UUID customerId, String channel, String status,
        String summary, Instant createdAt, Instant updatedAt
    ) {
        public static ConversationSummary from(Conversation c) {
            return new ConversationSummary(
                c.getId(), c.getCustomerId(), c.getChannel().name(),
                c.getStatus().name(), c.getSummary(), c.getCreatedAt(), c.getUpdatedAt()
            );
        }
    }

    public record ConversationDetail(
        UUID id, UUID customerId, String channel, String status,
        String summary, Instant createdAt, Instant updatedAt,
        List<MessageResponse> messages
    ) {}

    public record MessageResponse(UUID id, String role, String content, Instant createdAt) {
        public static MessageResponse from(Message m) {
            return new MessageResponse(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt());
        }
    }
}

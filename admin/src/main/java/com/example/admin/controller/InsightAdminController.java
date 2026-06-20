package com.example.admin.controller;

import com.example.core.tenant.TenantContext;
import com.example.memory.shared.InsightStatus;
import com.example.memory.shared.SharedInsight;
import com.example.memory.shared.SharedInsightRepository;
import com.example.memory.shared.SharedMemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/insights")
public class InsightAdminController {

    private final SharedMemoryService sharedMemoryService;
    private final SharedInsightRepository insightRepository;

    public InsightAdminController(SharedMemoryService sharedMemoryService,
                                   SharedInsightRepository insightRepository) {
        this.sharedMemoryService = sharedMemoryService;
        this.insightRepository = insightRepository;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<InsightResponse>> listPending() {
        var tenant = TenantContext.require();
        List<SharedInsight> pending = sharedMemoryService.getPendingInsights(tenant.getId());
        return ResponseEntity.ok(pending.stream().map(InsightResponse::from).toList());
    }

    @GetMapping
    public ResponseEntity<List<InsightResponse>> list(
            @RequestParam(required = false) String status) {
        var tenant = TenantContext.require();
        InsightStatus insightStatus = status != null
            ? InsightStatus.valueOf(status.toUpperCase())
            : InsightStatus.PENDING;
        List<SharedInsight> insights = insightRepository.findByTenantIdAndStatus(
            tenant.getId(), insightStatus);
        return ResponseEntity.ok(insights.stream().map(InsightResponse::from).toList());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id,
                                         @Valid @RequestBody ReviewRequest request) {
        sharedMemoryService.approveInsight(id, request.reviewerId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                        @Valid @RequestBody ReviewRequest request) {
        sharedMemoryService.rejectInsight(id, request.reviewerId());
        return ResponseEntity.ok().build();
    }

    public record ReviewRequest(@NotNull UUID reviewerId) {}

    public record InsightResponse(
        UUID id, UUID conversationId, String insight, InsightStatus status,
        UUID reviewedBy, Instant reviewedAt, Instant createdAt
    ) {
        public static InsightResponse from(SharedInsight i) {
            return new InsightResponse(
                i.getId(), i.getConversationId(), i.getInsight(), i.getStatus(),
                i.getReviewedBy(), i.getReviewedAt(), i.getCreatedAt()
            );
        }
    }
}

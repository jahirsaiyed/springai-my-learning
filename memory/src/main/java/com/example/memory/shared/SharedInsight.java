package com.example.memory.shared;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_insights")
public class SharedInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(nullable = false, columnDefinition = "text")
    private String insight;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InsightStatus status = InsightStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected SharedInsight() {}

    public SharedInsight(UUID tenantId, UUID conversationId, String insight) {
        this.tenantId = tenantId;
        this.conversationId = conversationId;
        this.insight = insight;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getConversationId() { return conversationId; }
    public String getInsight() { return insight; }
    public InsightStatus getStatus() { return status; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void approve(UUID reviewerId) {
        this.status = InsightStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewerId) {
        this.status = InsightStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }
}

package com.example.memory.semantic;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (effectiveFrom == null) effectiveFrom = createdAt;
    }

    protected KnowledgeDocument() {}

    public KnowledgeDocument(UUID tenantId, String title, SourceType sourceType) {
        this.tenantId = tenantId;
        this.title = title;
        this.sourceType = sourceType;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public SourceType getSourceType() { return sourceType; }
    public int getVersion() { return version; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveUntil() { return effectiveUntil; }
    public DocumentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
    public void setVersion(int version) { this.version = version; }
    public void setEffectiveUntil(Instant effectiveUntil) { this.effectiveUntil = effectiveUntil; }
    public void setStatus(DocumentStatus status) { this.status = status; }
}

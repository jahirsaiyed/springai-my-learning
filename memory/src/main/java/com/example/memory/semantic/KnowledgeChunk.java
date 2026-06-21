package com.example.memory.semantic;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Note: embedding vector is managed via native queries / VectorStore,
    // not mapped as a JPA field since JPA doesn't natively support pgvector types.

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected KnowledgeChunk() {}

    public KnowledgeChunk(KnowledgeDocument document, String content, int chunkIndex) {
        this.document = document;
        this.content = content;
        this.chunkIndex = chunkIndex;
    }

    public UUID getId() { return id; }
    public KnowledgeDocument getDocument() { return document; }
    public String getContent() { return content; }
    public int getChunkIndex() { return chunkIndex; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }

    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}

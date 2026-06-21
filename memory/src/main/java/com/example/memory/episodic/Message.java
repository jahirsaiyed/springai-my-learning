package com.example.memory.episodic;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected Message() {}

    public Message(MessageRole role, String content) {
        this.role = role;
        this.content = content;
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }

    void setConversation(Conversation conversation) { this.conversation = conversation; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}

package com.example.memory.procedural;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "procedure_executions")
public class ProcedureExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procedure_id", nullable = false)
    private Procedure procedure;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "state_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String stateJson;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status = ExecutionStatus.IN_PROGRESS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    protected ProcedureExecution() {}

    public ProcedureExecution(Procedure procedure, UUID conversationId) {
        this.procedure = procedure;
        this.conversationId = conversationId;
        this.stateJson = "{\"currentStep\":0,\"completedSteps\":[]}";
    }

    public UUID getId() { return id; }
    public Procedure getProcedure() { return procedure; }
    public UUID getConversationId() { return conversationId; }
    public String getStateJson() { return stateJson; }
    public ExecutionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
}

package com.example.memory.procedural;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "procedures")
public class Procedure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String domain;

    @Column(name = "workflow_yaml", nullable = false, columnDefinition = "text")
    private String workflowYaml;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcedureSource source;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcedureStatus status = ProcedureStatus.DRAFT;

    @Column(nullable = false)
    private int version = 1;

    @Column
    private String description;

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

    protected Procedure() {}

    public Procedure(UUID tenantId, String name, String domain, String workflowYaml, ProcedureSource source) {
        this.tenantId = tenantId;
        this.name = name;
        this.domain = domain;
        this.workflowYaml = workflowYaml;
        this.source = source;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDomain() { return domain; }
    public String getWorkflowYaml() { return workflowYaml; }
    public ProcedureSource getSource() { return source; }
    public ProcedureStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setWorkflowYaml(String workflowYaml) { this.workflowYaml = workflowYaml; }
    public void setStatus(ProcedureStatus status) { this.status = status; }
    public void setVersion(int version) { this.version = version; }
    public void setDescription(String description) { this.description = description; }
}

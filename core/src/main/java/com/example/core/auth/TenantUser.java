package com.example.core.auth;

import com.example.core.tenant.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_users", schema = "public",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "user_id"}))
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected TenantUser() {}

    public TenantUser(Tenant tenant, User user, TenantRole role) {
        this.tenant = tenant;
        this.user = user;
        this.role = role;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public User getUser() { return user; }
    public TenantRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRole(TenantRole role) { this.role = role; }
}

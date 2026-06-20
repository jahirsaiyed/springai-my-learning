package com.example.memory.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SharedInsightRepository extends JpaRepository<SharedInsight, UUID> {

    List<SharedInsight> findByTenantIdAndStatus(UUID tenantId, InsightStatus status);
}

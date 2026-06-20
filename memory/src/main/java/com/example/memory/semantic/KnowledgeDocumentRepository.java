package com.example.memory.semantic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findByTenantIdAndStatus(UUID tenantId, DocumentStatus status);

    @Query("""
        SELECT d FROM KnowledgeDocument d
        WHERE d.tenantId = :tenantId
        AND d.status = 'ACTIVE'
        AND d.effectiveFrom <= :asOf
        AND (d.effectiveUntil IS NULL OR d.effectiveUntil > :asOf)
        """)
    List<KnowledgeDocument> findEffectiveDocuments(UUID tenantId, Instant asOf);
}

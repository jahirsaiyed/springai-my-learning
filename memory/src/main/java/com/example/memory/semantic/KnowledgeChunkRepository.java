package com.example.memory.semantic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    @Modifying
    @Query("DELETE FROM KnowledgeChunk c WHERE c.document.id = :documentId")
    void deleteByDocumentId(UUID documentId);
}

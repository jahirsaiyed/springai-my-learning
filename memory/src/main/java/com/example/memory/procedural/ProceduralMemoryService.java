package com.example.memory.procedural;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for procedural memory operations.
 * Manages workflow definitions and their execution.
 */
public interface ProceduralMemoryService {

    Procedure createProcedure(UUID tenantId, String name, String domain,
                              String workflowYaml, ProcedureSource source);

    List<Procedure> findByDomain(UUID tenantId, String domain);

    Optional<Procedure> findById(UUID procedureId);

    void approve(UUID procedureId);

    List<Procedure> findPendingReview(UUID tenantId);
}

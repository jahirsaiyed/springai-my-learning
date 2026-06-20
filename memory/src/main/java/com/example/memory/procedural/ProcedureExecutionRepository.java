package com.example.memory.procedural;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcedureExecutionRepository extends JpaRepository<ProcedureExecution, UUID> {

    Optional<ProcedureExecution> findByConversationIdAndStatus(UUID conversationId, ExecutionStatus status);

    List<ProcedureExecution> findByProcedureId(UUID procedureId);
}

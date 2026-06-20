package com.example.memory.procedural;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultProceduralMemoryService implements ProceduralMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultProceduralMemoryService.class);

    private final ProcedureRepository procedureRepository;
    private final ProcedureExecutionRepository executionRepository;
    private final WorkflowParser workflowParser;

    public DefaultProceduralMemoryService(ProcedureRepository procedureRepository,
                                           ProcedureExecutionRepository executionRepository,
                                           WorkflowParser workflowParser) {
        this.procedureRepository = procedureRepository;
        this.executionRepository = executionRepository;
        this.workflowParser = workflowParser;
    }

    @Override
    @Transactional
    public Procedure createProcedure(UUID tenantId, String name, String domain,
                                      String workflowYaml, ProcedureSource source) {
        // Validate YAML parses correctly
        workflowParser.parse(workflowYaml);

        var procedure = new Procedure(tenantId, name, domain, workflowYaml, source);

        if (source == ProcedureSource.LEARNED) {
            procedure.setStatus(ProcedureStatus.PENDING_REVIEW);
        } else {
            procedure.setStatus(ProcedureStatus.APPROVED);
        }

        procedure = procedureRepository.save(procedure);
        log.info("Created procedure '{}' in domain '{}' for tenant {} (source: {})",
            name, domain, tenantId, source);
        return procedure;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Procedure> findByDomain(UUID tenantId, String domain) {
        return procedureRepository.findByTenantIdAndDomainAndStatus(
            tenantId, domain, ProcedureStatus.APPROVED);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Procedure> findById(UUID procedureId) {
        return procedureRepository.findById(procedureId);
    }

    @Override
    @Transactional
    public void approve(UUID procedureId) {
        Procedure procedure = procedureRepository.findById(procedureId)
            .orElseThrow(() -> new IllegalArgumentException("Procedure not found: " + procedureId));

        procedure.setStatus(ProcedureStatus.APPROVED);
        procedureRepository.save(procedure);
        log.info("Approved procedure '{}'", procedure.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Procedure> findPendingReview(UUID tenantId) {
        return procedureRepository.findByTenantIdAndStatus(tenantId, ProcedureStatus.PENDING_REVIEW);
    }

    @Transactional
    public ProcedureExecution startExecution(UUID procedureId, UUID conversationId) {
        Procedure procedure = procedureRepository.findById(procedureId)
            .orElseThrow(() -> new IllegalArgumentException("Procedure not found: " + procedureId));

        var execution = new ProcedureExecution(procedure, conversationId);
        return executionRepository.save(execution);
    }

    @Transactional
    public ProcedureExecution updateExecutionState(UUID executionId, String stateJson) {
        ProcedureExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        execution.setStateJson(stateJson);
        return executionRepository.save(execution);
    }

    @Transactional
    public void completeExecution(UUID executionId) {
        ProcedureExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        execution.setStatus(ExecutionStatus.COMPLETED);
        executionRepository.save(execution);
    }

    public WorkflowDefinition parseWorkflow(String yaml) {
        return workflowParser.parse(yaml);
    }
}

package com.example.admin.controller;

import com.example.core.tenant.TenantContext;
import com.example.memory.procedural.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/procedures")
public class ProcedureAdminController {

    private final ProceduralMemoryService proceduralMemoryService;
    private final ProcedureRepository procedureRepository;

    public ProcedureAdminController(ProceduralMemoryService proceduralMemoryService,
                                     ProcedureRepository procedureRepository) {
        this.proceduralMemoryService = proceduralMemoryService;
        this.procedureRepository = procedureRepository;
    }

    @PostMapping
    public ResponseEntity<ProcedureResponse> create(@Valid @RequestBody CreateProcedureRequest request) {
        var tenant = TenantContext.require();
        Procedure procedure = proceduralMemoryService.createProcedure(
            tenant.getId(), request.name(), request.domain(),
            request.workflowYaml(), request.source() != null ? request.source() : ProcedureSource.MANUAL
        );
        return ResponseEntity.ok(ProcedureResponse.from(procedure));
    }

    @GetMapping
    public ResponseEntity<List<ProcedureResponse>> list(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        var tenant = TenantContext.require();
        List<Procedure> procedures;
        if (domain != null && status != null) {
            procedures = procedureRepository.findByTenantIdAndDomainAndStatus(
                tenant.getId(), domain, ProcedureStatus.valueOf(status.toUpperCase()));
        } else if (status != null) {
            procedures = procedureRepository.findByTenantIdAndStatus(
                tenant.getId(), ProcedureStatus.valueOf(status.toUpperCase()));
        } else if (domain != null) {
            procedures = procedureRepository.findByTenantIdAndDomainAndStatus(
                tenant.getId(), domain, ProcedureStatus.APPROVED);
        } else {
            procedures = procedureRepository.findByTenantIdAndStatus(
                tenant.getId(), ProcedureStatus.APPROVED);
        }
        return ResponseEntity.ok(procedures.stream().map(ProcedureResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcedureResponse> get(@PathVariable UUID id) {
        return proceduralMemoryService.findById(id)
            .map(p -> ResponseEntity.ok(ProcedureResponse.from(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ProcedureResponse>> listPending() {
        var tenant = TenantContext.require();
        List<Procedure> pending = proceduralMemoryService.findPendingReview(tenant.getId());
        return ResponseEntity.ok(pending.stream().map(ProcedureResponse::from).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcedureResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateProcedureRequest request) {
        Procedure procedure = procedureRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Procedure not found: " + id));

        if (request.workflowYaml() != null) {
            procedure.setWorkflowYaml(request.workflowYaml());
        }
        if (request.description() != null) {
            procedure.setDescription(request.description());
        }
        procedure.setVersion(procedure.getVersion() + 1);
        procedure = procedureRepository.save(procedure);
        return ResponseEntity.ok(ProcedureResponse.from(procedure));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        proceduralMemoryService.approve(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        Procedure procedure = procedureRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Procedure not found: " + id));
        procedure.setStatus(ProcedureStatus.ARCHIVED);
        procedureRepository.save(procedure);
        return ResponseEntity.noContent().build();
    }

    public record CreateProcedureRequest(
        @NotBlank String name,
        @NotBlank String domain,
        @NotBlank String workflowYaml,
        String description,
        ProcedureSource source
    ) {}

    public record UpdateProcedureRequest(
        String workflowYaml,
        String description
    ) {}

    public record ProcedureResponse(
        UUID id, String name, String domain, String workflowYaml,
        ProcedureSource source, ProcedureStatus status, int version,
        String description, Instant createdAt, Instant updatedAt
    ) {
        public static ProcedureResponse from(Procedure p) {
            return new ProcedureResponse(
                p.getId(), p.getName(), p.getDomain(), p.getWorkflowYaml(),
                p.getSource(), p.getStatus(), p.getVersion(),
                p.getDescription(), p.getCreatedAt(), p.getUpdatedAt()
            );
        }
    }
}

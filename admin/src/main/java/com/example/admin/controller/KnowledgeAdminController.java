package com.example.admin.controller;

import com.example.core.tenant.TenantContext;
import com.example.memory.semantic.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {

    private final SemanticMemoryService semanticMemoryService;
    private final KnowledgeDocumentRepository documentRepository;

    public KnowledgeAdminController(SemanticMemoryService semanticMemoryService,
                                     KnowledgeDocumentRepository documentRepository) {
        this.semanticMemoryService = semanticMemoryService;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/documents")
    public ResponseEntity<DocumentResponse> ingestDocument(@Valid @RequestBody IngestDocumentRequest request) {
        var tenant = TenantContext.require();
        KnowledgeDocument doc = semanticMemoryService.ingestDocument(
            tenant.getId(), request.title(), request.sourceType(), request.content()
        );
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @RequestParam(required = false) String status) {
        var tenant = TenantContext.require();
        List<KnowledgeDocument> docs;
        if (status != null) {
            docs = documentRepository.findByTenantIdAndStatus(
                tenant.getId(), DocumentStatus.valueOf(status.toUpperCase()));
        } else {
            docs = documentRepository.findByTenantIdAndStatus(tenant.getId(), DocumentStatus.ACTIVE);
        }
        return ResponseEntity.ok(docs.stream().map(DocumentResponse::from).toList());
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id) {
        return documentRepository.findById(id)
            .map(d -> ResponseEntity.ok(DocumentResponse.from(d)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/documents/{id}/supersede")
    public ResponseEntity<Void> supersedeDocument(@PathVariable UUID id,
                                                    @Valid @RequestBody SupersedeRequest request) {
        semanticMemoryService.supersede(id, request.newContent());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        semanticMemoryService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    public ResponseEntity<List<String>> searchKnowledge(@Valid @RequestBody SearchRequest request) {
        var tenant = TenantContext.require();
        int topK = request.topK() != null ? request.topK() : 5;
        List<String> results = semanticMemoryService.search(tenant.getId(), request.query(), topK);
        return ResponseEntity.ok(results);
    }

    public record IngestDocumentRequest(
        @NotBlank String title,
        SourceType sourceType,
        @NotBlank String content
    ) {}

    public record SupersedeRequest(@NotBlank String newContent) {}

    public record SearchRequest(@NotBlank String query, Integer topK) {}

    public record DocumentResponse(
        UUID id, String title, SourceType sourceType, int version,
        DocumentStatus status, Instant effectiveFrom, Instant effectiveUntil, Instant createdAt
    ) {
        public static DocumentResponse from(KnowledgeDocument doc) {
            return new DocumentResponse(
                doc.getId(), doc.getTitle(), doc.getSourceType(), doc.getVersion(),
                doc.getStatus(), doc.getEffectiveFrom(), doc.getEffectiveUntil(), doc.getCreatedAt()
            );
        }
    }
}

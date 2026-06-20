package com.example.api.controller;

import com.example.core.tenant.Tenant;
import com.example.core.tenant.TenantSchemaMigrator;
import com.example.core.tenant.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final TenantSchemaMigrator tenantSchemaMigrator;

    public TenantController(TenantService tenantService, TenantSchemaMigrator tenantSchemaMigrator) {
        this.tenantService = tenantService;
        this.tenantSchemaMigrator = tenantSchemaMigrator;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(request.slug(), request.name());
        tenantSchemaMigrator.migrate(tenant.getSchemaName());
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    @GetMapping
    public ResponseEntity<List<TenantResponse>> list() {
        List<TenantResponse> tenants = tenantService.findAll().stream()
            .map(TenantResponse::from)
            .toList();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> get(@PathVariable UUID id) {
        return tenantService.findById(id)
            .map(t -> ResponseEntity.ok(TenantResponse.from(t)))
            .orElse(ResponseEntity.notFound().build());
    }

    public record CreateTenantRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String slug,
        @NotBlank String name
    ) {}

    public record TenantResponse(UUID id, String slug, String name, String schemaName, boolean active) {
        public static TenantResponse from(Tenant tenant) {
            return new TenantResponse(
                tenant.getId(), tenant.getSlug(), tenant.getName(),
                tenant.getSchemaName(), tenant.isActive()
            );
        }
    }
}

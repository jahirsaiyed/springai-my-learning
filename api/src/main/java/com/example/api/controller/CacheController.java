package com.example.api.controller;

import com.example.memory.cache.CacheMetrics;
import com.example.memory.cache.SemanticCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final SemanticCacheService cacheService;

    public CacheController(SemanticCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/stats")
    public ResponseEntity<CacheMetrics.CacheStats> globalStats() {
        return ResponseEntity.ok(cacheService.getGlobalStats());
    }

    @GetMapping("/stats/{tenantId}")
    public ResponseEntity<CacheMetrics.CacheStats> tenantStats(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(cacheService.getStats(tenantId));
    }

    @DeleteMapping("/invalidate/{tenantId}")
    public ResponseEntity<Void> invalidate(@PathVariable UUID tenantId) {
        cacheService.invalidateTenant(tenantId);
        return ResponseEntity.noContent().build();
    }
}

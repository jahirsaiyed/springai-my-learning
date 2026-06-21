package com.example.admin.controller;

import com.example.ecommerce.entity.UserCustomerMapping;
import com.example.ecommerce.repository.UserCustomerMappingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/user-customer-mappings")
public class UserCustomerMappingController {

    private final UserCustomerMappingRepository mappingRepository;

    public UserCustomerMappingController(UserCustomerMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @PostMapping
    public ResponseEntity<?> createMapping(@Valid @RequestBody CreateMappingRequest request) {
        try {
            var mapping = new UserCustomerMapping(request.userId(), request.customerId());
            var saved = mappingRepository.save(mapping);
            return ResponseEntity.status(HttpStatus.CREATED).body(MappingResponse.from(saved));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Mapping already exists or references invalid user/customer"));
        }
    }

    @GetMapping
    public ResponseEntity<List<MappingResponse>> listMappings() {
        var mappings = mappingRepository.findAll().stream()
                .map(MappingResponse::from)
                .toList();
        return ResponseEntity.ok(mappings);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable Integer id) {
        if (!mappingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        mappingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateMappingRequest(
            @NotNull UUID userId,
            @NotBlank String customerId
    ) {}

    public record MappingResponse(Integer id, UUID userId, String customerId, LocalDateTime createdAt) {
        public static MappingResponse from(UserCustomerMapping m) {
            return new MappingResponse(m.getId(), m.getUserId(), m.getCustomerId(), m.getCreatedAt());
        }
    }

    public record ErrorResponse(String error) {}
}

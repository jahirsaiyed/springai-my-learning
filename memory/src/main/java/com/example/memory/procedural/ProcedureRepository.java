package com.example.memory.procedural;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcedureRepository extends JpaRepository<Procedure, UUID> {

    List<Procedure> findByTenantIdAndDomainAndStatus(UUID tenantId, String domain, ProcedureStatus status);

    List<Procedure> findByTenantIdAndStatus(UUID tenantId, ProcedureStatus status);
}

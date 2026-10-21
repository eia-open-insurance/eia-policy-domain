package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for the legal entity catalog. The expert owns the code
 * and license rules, the entity-type and status vocabulary, and tenant-scoped
 * uniqueness; the domain validates the request shape before delegating.
 */
@Service
public class LegalEntityService {
    private final PolicyClient client;

    public LegalEntityService(PolicyClient client) {
        this.client = client;
    }

    public List<LegalEntityDto> list(UUID tenantId) {
        return client.findLegalEntities(tenantId);
    }

    public LegalEntityDto get(UUID tenantId, UUID legalEntityId) {
        return client.findLegalEntity(tenantId, legalEntityId);
    }

    public LegalEntityDto create(UUID tenantId, CreateLegalEntityCommand command) {
        return client.createLegalEntity(tenantId, command);
    }
}

package com.eia.policy.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a legal entity: the regulated carrier, holding, MGA, or
 * service company that issues or supports a DIRECT policy (SPEC 10.11.2,
 * 11.2.16). The Policy expert owns the code, the entity-type and status
 * vocabularies, and the license data.
 */
public record LegalEntityDto(
        UUID legalEntityId,
        String code,
        String name,
        String entityType,
        String jurisdiction,
        String regulator,
        String licenseNumber,
        String status,
        Instant createdAt
) {
}

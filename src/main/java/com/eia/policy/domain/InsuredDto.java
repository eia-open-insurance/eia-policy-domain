package com.eia.policy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Insured party projection. */
public record InsuredDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        UUID partyId,
        String insuredRole,
        String displayName,
        String certificateNumber,
        String scope,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        Instant createdAt
) {
}

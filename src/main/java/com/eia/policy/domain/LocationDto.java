package com.eia.policy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Scheduled location projection. */
public record LocationDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        String locationCode,
        String addressLine1,
        String addressLine2,
        String city,
        String stateRegion,
        String postalCode,
        String country,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        Instant createdAt
) {
}

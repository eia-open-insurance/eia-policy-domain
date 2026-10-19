package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Coverage grant projection. */
public record CoverageDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        String coverageCode,
        String coverageName,
        String coveragePart,
        String occurrenceBasis,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal coinsurancePct,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        int sequence,
        Instant createdAt
) {
}

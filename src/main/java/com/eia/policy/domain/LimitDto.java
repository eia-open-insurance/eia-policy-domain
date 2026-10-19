package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Limit schedule projection for one coverage. */
public record LimitDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        UUID coverageId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal perOccurrenceAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aggregateAmount,
        String aggregateBasis,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal sublimitAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal deductibleAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal coinsurancePct,
        String currency,
        String erosionBasis,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        int sequence,
        Instant createdAt
) {
}

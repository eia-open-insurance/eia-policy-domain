package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of a policy program. A program is the commercial portfolio
 * that groups policies placed together for one insured or one mass tort
 * exposure; the Policy expert owns its numbering, vocabulary, and state.
 *
 * <p>The capacity members are optional: {@code capacityAmount} and
 * {@code capacityCurrency} are the program's capacity limit when one is
 * defined, while {@code usedAmount}, {@code utilizationPct}, and
 * {@code policyCount} are expert-derived aggregates over the policies assigned
 * to the program. The domain exposes them unchanged so a caller can see how
 * much of the program capacity is committed without a second read.</p>
 */
public record ProgramDto(
        UUID programId,
        String programNumber,
        String name,
        String programType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        UUID leadPolicyId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal capacityAmount,
        String capacityCurrency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal usedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal utilizationPct,
        Integer policyCount,
        Instant createdAt
) {
}

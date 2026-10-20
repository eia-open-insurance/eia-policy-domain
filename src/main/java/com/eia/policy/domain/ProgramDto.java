package com.eia.policy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of a policy program. A program is the commercial portfolio
 * that groups policies placed together for one insured or one mass tort
 * exposure; the Policy expert owns its numbering, vocabulary, and state.
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
        Instant createdAt
) {
}

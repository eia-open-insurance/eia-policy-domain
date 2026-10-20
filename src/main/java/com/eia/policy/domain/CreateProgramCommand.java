package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a program. The expert owns the program number and status, the
 * program-type vocabulary, tenant-scoped uniqueness, and the effective-date
 * rules; the domain validates only the request shape.
 */
public record CreateProgramCommand(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 32) String programType,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo,
        UUID leadPolicyId
) {
}

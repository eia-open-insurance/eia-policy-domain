package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a program. The expert owns the program number and status, the
 * program-type vocabulary, tenant-scoped uniqueness, the effective-date rules,
 * and the capacity derivation; the domain validates only the request shape.
 *
 * <p>{@code capacityAmount} and {@code capacityCurrency} are an optional pair:
 * a program without either has no recorded capacity limit, while supplying one
 * requires the other so the amount is unambiguous. The remaining capacity
 * members on {@link ProgramDto} are server-owned.</p>
 */
public record CreateProgramCommand(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 32) String programType,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo,
        UUID leadPolicyId,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4)
        BigDecimal capacityAmount,
        @Pattern(regexp = "[A-Z]{3}", message = "capacityCurrency must be an ISO 4217 code")
        String capacityCurrency
) {
}

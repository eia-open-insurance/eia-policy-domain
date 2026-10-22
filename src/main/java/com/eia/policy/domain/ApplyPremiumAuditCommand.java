package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Applies an audited premium to a policy. The Policy expert owns the audit
 * eligibility, the new policy version, the premium delta, the approval gate,
 * and the posting event; the domain validates only the request shape.
 *
 * <p>{@code approvedBy} and {@code approvalRef} are optional for a normal
 * audit; the expert requires them when the effective date is before inception
 * or would regress the version timeline. When supplied they are forwarded
 * unchanged; the expert decides whether the audit requires them.</p>
 */
public record ApplyPremiumAuditCommand(
        @NotNull @DecimalMin(value = "0.0", inclusive = true)
        @Digits(integer = 15, fraction = 4) BigDecimal auditedPremium,
        @NotNull LocalDate effectiveDate,
        @NotBlank @Size(max = 500) String reason,
        UUID approvedBy,
        UUID approvalRef
) {
}

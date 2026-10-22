package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Appends a positive premium adjustment installment to the policy's ACTIVE
 * billing plan. The expert derives the amount from the policy written premium
 * less the plan total, so the command carries only the due date and the audit
 * reason; a zero or negative remainder is a {@code 400 NO_PREMIUM_ADJUSTMENT}
 * because a premium decrease is a credit-note concern.
 *
 * <p>{@code dueDate} is required and may not precede the policy effective date
 * (the effective date itself is accepted). {@code reason} is required and is
 * carried on the emitted {@code policy.billing-plan.adjusted} event.</p>
 */
public record BillingAdjustmentCommand(
        @NotNull LocalDate dueDate,
        @NotBlank @Size(max = 500) String reason
) {
}

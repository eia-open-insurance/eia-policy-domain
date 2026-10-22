package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

/**
 * Public envelope for a positive premium billing adjustment: the updated plan,
 * the appended installment, the derived adjustment amount, and the resulting
 * plan total. The expert owns the premium remainder, the appended installment,
 * and the plan-total movement; the domain publishes the expert envelope with
 * the public billing plan and installment projections.
 *
 * <p>The plan member carries the plan identity and totals after the adjustment.
 * The appended installment is also published separately as {@code installment};
 * the plan member does not repeat the full schedule.</p>
 */
public record BillingAdjustmentResult(
        BillingPlan plan,
        Installment installment,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal adjustmentAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal newTotalPremium
) {
}

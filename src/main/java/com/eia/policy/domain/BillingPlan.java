package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public projection of a policy billing plan and its installment schedule.
 * Business billing is owned by Policy (SPEC 11.2.13); the domain proxies the
 * expert projection unchanged.
 *
 * <p>{@code summary} carries the plan totals, including the credited amount
 * applied against open installments and the net outstanding balance. When the
 * expert omits it, the domain derives the summary from the plan total and the
 * installment rows so an older projection still produces a coherent public
 * reading.</p>
 */
public record BillingPlan(
        UUID planId,
        String planNumber,
        UUID policyId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPremium,
        String currency,
        int installmentCount,
        String frequency,
        LocalDate firstDueDate,
        String status,
        List<Installment> installments,
        BillingSummary summary
) {
    public BillingPlan {
        installments = installments == null ? List.of() : List.copyOf(installments);
        summary = summary == null
                ? BillingSummary.of(totalPremium, currency, installments)
                : summary;
    }

    /** Compatibility constructor for plans projected before the summary was exposed. */
    public BillingPlan(UUID planId, String planNumber, UUID policyId, BigDecimal totalPremium,
                       String currency, int installmentCount, String frequency,
                       LocalDate firstDueDate, String status, List<Installment> installments) {
        this(planId, planNumber, policyId, totalPremium, currency, installmentCount,
                frequency, firstDueDate, status, installments, null);
    }
}

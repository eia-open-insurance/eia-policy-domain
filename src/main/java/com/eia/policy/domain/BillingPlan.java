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
        List<Installment> installments
) {
    public BillingPlan {
        installments = installments == null ? List.of() : List.copyOf(installments);
    }
}

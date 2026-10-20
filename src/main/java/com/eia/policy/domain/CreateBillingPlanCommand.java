package com.eia.policy.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Creates the billing plan for a policy. The expert owns the installment
 * schedule, the amount split, and the frequency rules; the domain validates
 * only the request shape and the 1..24 installment window.
 */
public record CreateBillingPlanCommand(
        @NotNull @Min(1) @Max(24) Integer installmentCount,
        @NotBlank @Size(max = 20) String frequency,
        @NotNull LocalDate firstDueDate
) {
}

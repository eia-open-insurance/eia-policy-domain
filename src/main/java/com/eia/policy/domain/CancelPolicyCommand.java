package com.eia.policy.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Cancel a policy with a reason and an effective date. */
public record CancelPolicyCommand(
        @NotBlank String cancellationType,
        @NotBlank @Size(max = 200) String reason,
        @NotNull LocalDate effectiveDate,
        @Min(0) Integer noticeDays,
        UUID approvedBy,
        UUID approvalRef
) {
}

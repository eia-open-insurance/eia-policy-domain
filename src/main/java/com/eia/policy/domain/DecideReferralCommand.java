package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Decides the pending structured referral on one underwriting submission
 * (SPEC 11.2.14 {@code referral}). The expert owns the {@code APPROVED}/
 * {@code DECLINED} vocabulary and the pending-referral rule; the domain
 * validates only the required decision, required decider, and optional notes
 * with the expert's length caps.
 */
public record DecideReferralCommand(
        @NotBlank @Size(max = 32) String decision,
        @NotBlank @Size(max = 128) String decidedBy,
        @Size(max = 2000) String notes
) {
}

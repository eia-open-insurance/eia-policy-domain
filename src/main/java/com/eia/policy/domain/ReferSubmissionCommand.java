package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Opens or replaces the structured referral on one underwriting submission
 * (SPEC 11.2.14 {@code referral}). The expert owns the referrable state, the
 * decision lifecycle, and the authority vocabulary, so the domain validates
 * only the request shape: a required reason, required authority, required
 * assignee, and optional notes, with the expert's length caps.
 *
 * <p>The expert contract names the members {@code authority}, {@code referredTo},
 * and {@code notes}; the SPEC column aliases are also accepted so a client
 * written against the data dictionary can use them while rollout settles.</p>
 */
public record ReferSubmissionCommand(
        @NotBlank @Size(max = 64) String reason,
        @NotBlank @Size(max = 64) @JsonAlias("requiredAuthority") String authority,
        @NotBlank @Size(max = 128) @JsonAlias("assignedTo") String referredTo,
        @Size(max = 2000) @JsonAlias("comments") String notes
) {
}

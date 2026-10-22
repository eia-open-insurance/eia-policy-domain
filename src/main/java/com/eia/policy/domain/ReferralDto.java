package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * One review round of an underwriting submission's referral history (SPEC
 * 11.2.14 referral). The Policy expert owns the append-only history: every
 * accepted refer command opens a round with {@code decision=PENDING}, and the
 * decision command closes the newest pending round with the decider and
 * instant. The domain publishes the expert canonical projection unchanged.
 *
 * <p>The current referral is still projected on the submission itself; this
 * record is the history behind it, newest first. {@code reason},
 * {@code authority}, and {@code referredTo} are the details captured when the
 * round was opened, and {@code notes} is the referral narrative (replaced by a
 * non-blank decision note, matching the submission projection).
 * {@code decidedBy} and {@code decidedAt} are null while the round is pending.
 * The expert never leaks its tenant column, so the projection carries no
 * {@code tenantId}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReferralDto(
        UUID referralId,
        UUID submissionId,
        String reason,
        String authority,
        String referredTo,
        String notes,
        String decision,
        String decidedBy,
        Instant decidedAt,
        Instant createdAt
) {
}

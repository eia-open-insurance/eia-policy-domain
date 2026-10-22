package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Advances a credit note through its status lifecycle. The expert owns the
 * target vocabulary ({@code ISSUED}, {@code APPLIED}, {@code CANCELLED},
 * {@code REVERSED}) and the allowed transitions, so an unknown status or an
 * invalid transition is an expert {@code 400}/{@code 409} problem.
 *
 * <p>{@code installmentId} is optional and only meaningful for an {@code
 * APPLIED} transition: it names the open installment the credit is posted
 * against. The domain forwards it only when the target status is {@code
 * APPLIED}; the expert validates the installment, its tenant/policy scope, its
 * open status, and the remaining outstanding amount.</p>
 *
 * <p>{@code reason} is optional and only meaningful for a {@code REVERSED}
 * transition: it explains why the applied credit is released. The domain
 * forwards it only when the target status is {@code REVERSED}; the expert
 * requires a non-blank reason capped at 500 characters for that transition and
 * ignores it for every other status.</p>
 */
public record UpdateCreditNoteStatusCommand(
        @NotBlank String status,
        UUID installmentId,
        @Size(max = 500) String reason
) {
    /** Compatibility constructor for status changes that carry no installment or reason. */
    public UpdateCreditNoteStatusCommand(String status) {
        this(status, null, null);
    }

    /** Compatibility constructor for an APPLIED status change without a reason. */
    public UpdateCreditNoteStatusCommand(String status, UUID installmentId) {
        this(status, installmentId, null);
    }
}

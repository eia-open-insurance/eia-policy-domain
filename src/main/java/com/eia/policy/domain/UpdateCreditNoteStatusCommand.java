package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Advances a credit note through its status lifecycle. The expert owns the
 * target vocabulary ({@code ISSUED}, {@code APPLIED}, {@code CANCELLED}) and
 * the allowed transitions, so an unknown status or an invalid transition is an
 * expert {@code 400}/{@code 409} problem.
 *
 * <p>{@code installmentId} is optional and only meaningful for an {@code
 * APPLIED} transition: it names the open installment the credit is posted
 * against. The domain forwards it only when the target status is {@code
 * APPLIED}; the expert validates the installment, its tenant/policy scope, its
 * open status, and the remaining outstanding amount.</p>
 */
public record UpdateCreditNoteStatusCommand(
        @NotBlank String status,
        UUID installmentId
) {
    /** Compatibility constructor for status changes that carry no installment. */
    public UpdateCreditNoteStatusCommand(String status) {
        this(status, null);
    }
}

package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * Advances a credit note through its status lifecycle. The expert owns the
 * target vocabulary ({@code ISSUED}, {@code APPLIED}, {@code CANCELLED}) and
 * the allowed transitions, so an unknown status or an invalid transition is an
 * expert {@code 400}/{@code 409} problem.
 */
public record UpdateCreditNoteStatusCommand(
        @NotBlank String status
) {
}

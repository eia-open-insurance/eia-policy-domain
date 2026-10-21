package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Requests a draft credit note against a policy. The expert owns the note
 * number, the initial {@code DRAFT} status, the policy-currency match, the
 * reason cap, the approval gate, and the status transitions; the domain
 * validates only the request shape.
 */
public record CreateCreditNoteCommand(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 500) String reason
) {
}

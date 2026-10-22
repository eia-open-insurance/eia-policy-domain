package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requests a draft credit note against a policy. The expert owns the note
 * number, the initial {@code DRAFT} status, the policy-currency match, the
 * reason cap, the approval gate, and the status transitions; the domain
 * validates only the request shape.
 *
 * <p>{@code cancellationId} is optional and links the note to the cancellation
 * whose return premium it credits. The domain forwards it only when supplied;
 * the expert validates that the cancellation exists in the tenant/policy scope
 * and is eligible for a credit note, so an unknown or ineligible cancellation
 * is a preserved {@code 400}/{@code 404}/{@code 409} problem.</p>
 */
public record CreateCreditNoteCommand(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 500) String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID cancellationId
) {
    /** Compatibility constructor for requests that predate the cancellation link. */
    public CreateCreditNoteCommand(BigDecimal amount, String currency, String reason) {
        this(amount, currency, reason, null);
    }
}

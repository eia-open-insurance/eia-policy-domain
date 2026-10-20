package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Updates the payment state of one installment. A payment id is supplied when
 * the new state records a payment; the expert validates the transition.
 */
public record UpdateInstallmentCommand(
        @NotBlank @Size(max = 20) String status,
        UUID paymentId
) {
}

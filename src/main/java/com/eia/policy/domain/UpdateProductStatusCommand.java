package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Retires or reactivates a product. The expert owns the {@code ACTIVE} /
 * {@code RETIRED} vocabulary and treats replaying the current status as an
 * idempotent no-op, so the domain validates only the presence and shape of the
 * member before delegating.
 */
public record UpdateProductStatusCommand(
        @NotBlank @Size(max = 32) String status
) {
}

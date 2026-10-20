package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Records one dunning action against an installment. The domain validates the
 * request shape only; the expert validates the level/channel vocabulary, the
 * installment's collectible state, and whether the escalation level was already
 * sent.
 */
public record CreateDunningActionCommand(
        @NotBlank String level,
        @NotBlank String channel,
        @Size(max = 2000) String notes
) {
}

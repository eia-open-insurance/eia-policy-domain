package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An idempotent erosion fact for one policy limit. */
public record ErosionCommand(
        @NotNull UUID limitId,
        @NotNull UUID claimId,
        @NotNull UUID allocationId,
        @NotNull @Min(1) Long sourceVersion,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
        BigDecimal paidAmount,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
        BigDecimal reservedAmount,
        @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
        BigDecimal recoveredAmount,
        Instant asOf
) {
}

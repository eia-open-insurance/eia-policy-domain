package com.eia.policy.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Add a coverage grant with its limit schedule to a policy. */
public record CreateCoverageCommand(
        @NotBlank @Size(max = 32) String coverageCode,
        @NotBlank @Size(max = 160) String coverageName,
        @NotBlank @Pattern(regexp = "[A-D]", message = "coveragePart must be A, B, C, or D")
        String coveragePart,
        @NotBlank String occurrenceBasis,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "1.0", inclusive = true)
        @Digits(integer = 1, fraction = 6) BigDecimal coinsurancePct,
        @NotNull @Valid LimitCommand limit
) {
    /** Limit schedule accepted with a coverage. */
    public record LimitCommand(
            @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal perOccurrenceAmount,
            @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal aggregateAmount,
            String aggregateBasis,
            @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
            BigDecimal sublimitAmount,
            @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
            BigDecimal deductibleAmount,
            @DecimalMin(value = "0.0", inclusive = true) @DecimalMax(value = "1.0", inclusive = true)
            @Digits(integer = 1, fraction = 6) BigDecimal coinsurancePct,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 code")
            String currency,
            String erosionBasis
    ) {
    }
}

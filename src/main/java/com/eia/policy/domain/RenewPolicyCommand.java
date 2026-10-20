package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Renew a policy into a successor term. The new term dates are required;
 * premium and limit overrides are optional and default to the expiring term in
 * the Policy expert.
 */
public record RenewPolicyCommand(
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expirationDate,
        @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
        BigDecimal premiumAmount,
        @Pattern(regexp = "[A-Z]{3}", message = "premiumCurrency must be an ISO 4217 code")
        String premiumCurrency,
        @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 15, fraction = 4)
        BigDecimal limitAmount,
        @Pattern(regexp = "[A-Z]{3}", message = "limitCurrency must be an ISO 4217 code")
        String limitCurrency
) {
}

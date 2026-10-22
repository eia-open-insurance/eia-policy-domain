package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Converts one underwriting submission into a policy (SPEC 11.2.14 bind/
 * conversion slice). The expert owns the conversion eligibility, the
 * submission-to-policy linkage, and the quote/bind workflow, so this body is
 * limited to the policy terms the caller supplies: the written premium and its
 * currency, plus the optional expiration override. The submission's requested
 * effective date, limits, tort codes, and applicant remain the expert's
 * projection.
 */
public record ConvertSubmissionCommand(
        @NotNull @DecimalMin(value = "0.0", inclusive = true)
        @Digits(integer = 15, fraction = 4) BigDecimal premiumAmount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "premiumCurrency must be an ISO 4217 code")
        String premiumCurrency,
        LocalDate expirationDate
) {
}

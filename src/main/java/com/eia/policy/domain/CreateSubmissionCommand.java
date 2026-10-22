package com.eia.policy.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Creates one underwriting submission (SPEC 11.2.14). The expert owns the
 * submission number and the RECEIVED-to-terminal status workflow, so this body
 * is the expert canonical create contract: the applicant and product identify
 * the risk, the requested term and limit describe the coverage sought, and the
 * optional tort codes and notes carry the intake detail.
 *
 * <p>The validation caps mirror the expert's HTTP contract: product code 64
 * characters, a three-letter currency, a non-negative limit, at most 20 tort
 * codes of at most 32 characters each, and notes of at most 2000
 * characters.</p>
 */
public record CreateSubmissionCommand(
        @NotNull UUID applicantPartyId,
        @NotBlank @Size(max = 64) String productCode,
        @NotNull LocalDate requestedEffectiveDate,
        @NotNull @DecimalMin("0.0") BigDecimal requestedLimitAmount,
        @NotBlank @Size(min = 3, max = 3) String requestedLimitCurrency,
        @Size(max = 20) List<@NotBlank @Size(max = 32) String> tortCodes,
        @Size(max = 2000) String notes
) {
}

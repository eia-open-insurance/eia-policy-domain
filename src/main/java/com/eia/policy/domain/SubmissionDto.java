package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public projection of one underwriting submission (SPEC 11.2.14). The Policy
 * expert owns the submission number, the applicant/product rules, the status
 * transitions, and the quote/bind workflow; the domain publishes the expert
 * canonical members plus the request tenant scope.
 *
 * <p>The expert deliberately does not leak the tenant column, so {@code tenantId}
 * is filled from the scoped request. {@code version} is the aggregate
 * representation marker used for the strong {@code ETag}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmissionDto(
        UUID submissionId,
        String submissionNumber,
        UUID tenantId,
        UUID applicantPartyId,
        String productCode,
        LocalDate requestedEffectiveDate,
        BigDecimal requestedLimitAmount,
        String requestedLimitCurrency,
        List<String> tortCodes,
        String notes,
        String status,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Compatibility constructor for callers that order the two timestamps
     * before the optional aggregate version.
     */
    public SubmissionDto(UUID submissionId, String submissionNumber, UUID tenantId,
                         UUID applicantPartyId, String productCode,
                         LocalDate requestedEffectiveDate, BigDecimal requestedLimitAmount,
                         String requestedLimitCurrency, List<String> tortCodes,
                         String notes, String status, Instant createdAt, Instant updatedAt,
                         Integer version) {
        this(submissionId, submissionNumber, tenantId, applicantPartyId, productCode,
                requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
                tortCodes, notes, status, version, createdAt, updatedAt);
    }
}

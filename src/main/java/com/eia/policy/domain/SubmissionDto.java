package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public projection of one underwriting submission (SPEC 11.2.14). The Policy
 * expert owns the submission number, the applicant/product rules, the status
 * transitions, the quote/bind workflow, and the structured referral; the domain
 * publishes the expert canonical members plus the request tenant scope.
 *
 * <p>The expert deliberately does not leak the tenant column, so {@code tenantId}
 * is filled from the scoped request. {@code version} is the aggregate
 * representation marker used for the strong {@code ETag}. {@code policyId} is
 * the policy created by a conversion when the expert exposes that link; it is
 * omitted from the payload while an expert does not publish it.</p>
 *
 * <p>The referral members mirror the expert's structured referral record: the
 * reason, required authority, and assignee are recorded while the decision is
 * {@code PENDING}; a decision adds the {@code APPROVED}/{@code DECLINED}
 * outcome, the decider, and the decision timestamp. They are omitted until a
 * referral is opened so the intake projection keeps its existing shape.</p>
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
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID policyId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referralReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referralAuthority,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referredTo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referralNotes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referralDecision,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referralDecidedBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant referralDecidedAt
) {
    /**
     * Compatibility constructor for callers that order the two timestamps
     * before the optional aggregate version and do not project the converted
     * policy link or a referral.
     */
    public SubmissionDto(UUID submissionId, String submissionNumber, UUID tenantId,
                         UUID applicantPartyId, String productCode,
                         LocalDate requestedEffectiveDate, BigDecimal requestedLimitAmount,
                         String requestedLimitCurrency, List<String> tortCodes,
                         String notes, String status, Instant createdAt, Instant updatedAt,
                         Integer version) {
        this(submissionId, submissionNumber, tenantId, applicantPartyId, productCode,
                requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
                tortCodes, notes, status, version, createdAt, updatedAt, null,
                null, null, null, null, null, null, null);
    }

    /**
     * Compatibility constructor for callers that project the aggregate version
     * but not the converted policy link or a referral.
     */
    public SubmissionDto(UUID submissionId, String submissionNumber, UUID tenantId,
                         UUID applicantPartyId, String productCode,
                         LocalDate requestedEffectiveDate, BigDecimal requestedLimitAmount,
                         String requestedLimitCurrency, List<String> tortCodes,
                         String notes, String status, Integer version, Instant createdAt,
                         Instant updatedAt) {
        this(submissionId, submissionNumber, tenantId, applicantPartyId, productCode,
                requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
                tortCodes, notes, status, version, createdAt, updatedAt, null,
                null, null, null, null, null, null, null);
    }

    /**
     * Compatibility constructor for the pre-referral canonical projection with
     * the converted policy link and no referral members.
     */
    public SubmissionDto(UUID submissionId, String submissionNumber, UUID tenantId,
                         UUID applicantPartyId, String productCode,
                         LocalDate requestedEffectiveDate, BigDecimal requestedLimitAmount,
                         String requestedLimitCurrency, List<String> tortCodes,
                         String notes, String status, int version, Instant createdAt,
                         Instant updatedAt, UUID policyId) {
        this(submissionId, submissionNumber, tenantId, applicantPartyId, productCode,
                requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
                tortCodes, notes, status, version, createdAt, updatedAt, policyId,
                null, null, null, null, null, null, null);
    }

    /**
     * Compatibility constructor that places the structured referral before the
     * aggregate version and projects the converted policy last.
     */
    public SubmissionDto(UUID submissionId, String submissionNumber, UUID tenantId,
                         UUID applicantPartyId, String productCode,
                         LocalDate requestedEffectiveDate, BigDecimal requestedLimitAmount,
                         String requestedLimitCurrency, List<String> tortCodes,
                         String notes, String status, String referralReason,
                         String referralAuthority, String referredTo, String referralNotes,
                         String referralDecision, String referralDecidedBy,
                         Instant referralDecidedAt, Integer version, Instant createdAt,
                         Instant updatedAt, UUID policyId) {
        this(submissionId, submissionNumber, tenantId, applicantPartyId, productCode,
                requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
                tortCodes, notes, status, version, createdAt, updatedAt, policyId,
                referralReason, referralAuthority, referredTo, referralNotes,
                referralDecision, referralDecidedBy, referralDecidedAt);
    }

    /**
     * Compatibility constructor that places the structured referral before the
     * aggregate version and projects no converted policy.
     */
    public SubmissionDto(UUID submissionId, String submissionNumber, UUID tenantId,
                         UUID applicantPartyId, String productCode,
                         LocalDate requestedEffectiveDate, BigDecimal requestedLimitAmount,
                         String requestedLimitCurrency, List<String> tortCodes,
                         String notes, String status, String referralReason,
                         String referralAuthority, String referredTo, String referralNotes,
                         String referralDecision, String referralDecidedBy,
                         Instant referralDecidedAt, Integer version, Instant createdAt,
                         Instant updatedAt) {
        this(submissionId, submissionNumber, tenantId, applicantPartyId, productCode,
                requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
                tortCodes, notes, status, version, createdAt, updatedAt, null,
                referralReason, referralAuthority, referredTo, referralNotes,
                referralDecision, referralDecidedBy, referralDecidedAt);
    }
}

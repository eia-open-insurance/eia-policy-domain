package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public projection of one underwriting submission (SPEC 11.2.14). The Policy
 * expert owns the submission number, the product/clearance/referral/quote
 * workflow, the status vocabulary, and the applicant and exposure documents;
 * the domain publishes the expert row with the same camel-case members.
 *
 * <p>{@code requestedLimits} and {@code exposures} are free-form JSON documents
 * carried through unchanged, so an expert can store the SPEC {@code jsonb}
 * shapes without the domain inventing a partial model. {@code version} is
 * optional: when the expert supplies it, the domain uses it for the strong
 * {@code ETag}; an older expert may omit it.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmissionDto(
        UUID submissionId,
        String submissionNumber,
        UUID applicantPartyId,
        UUID productId,
        UUID programId,
        LocalDate requestedEffectiveDate,
        JsonNode requestedLimits,
        List<String> requestedCoverages,
        List<String> tortCodes,
        JsonNode exposures,
        String status,
        UUID owner,
        Instant receivedAt,
        Instant updatedAt,
        Integer version
) {
}

package com.eia.policy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Creates one underwriting submission (SPEC 11.2.14). The expert owns the
 * submission number and the received/clearance/rating/referral/quote workflow,
 * so this body carries only the applicant, product, and requested risk facts.
 *
 * <p>{@code applicantPartyId} and {@code productId} are required. The requested
 * effective date, limits, coverages, tort codes, exposures, owner, and an
 * externally supplied submission number are optional. The two JSON documents
 * are passed through unchanged to the expert.</p>
 */
public record CreateSubmissionCommand(
        @Size(max = 32) String submissionNumber,
        @NotNull UUID applicantPartyId,
        @NotNull UUID productId,
        UUID programId,
        LocalDate requestedEffectiveDate,
        JsonNode requestedLimits,
        List<String> requestedCoverages,
        List<String> tortCodes,
        JsonNode exposures,
        UUID owner
) {
}

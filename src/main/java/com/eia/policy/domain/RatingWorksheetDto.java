package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of one expert rating worksheet. The expert owns the
 * worksheet identity, the normalized inputs, the computed premium, the
 * canonical SHA-256, and the creation attribution; the domain publishes the
 * expert row with the same camel-case members and four-digit money strings.
 *
 * <p>{@code exposureAmount} is absent for {@code FLAT}. {@code status} is
 * always {@code CALCULATED}: whether the calculation was applied is observable
 * through the policy premium and its bumped aggregate version.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RatingWorksheetDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        String ratingMethod,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal baseRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal exposureAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal factor,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal computedPremium,
        String currency,
        String calcHash,
        String status,
        Instant createdAt,
        UUID createdBy
) {
}

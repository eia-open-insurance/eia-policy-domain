package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Public projection of an immutable policy version. */
public record PolicyVersionDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        int versionNumber,
        String policyVersionNumber,
        String changeType,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant issuedAt,
        UUID issuedBy,
        UUID approvedBy,
        UUID transactionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premiumDeltaAmount,
        String premiumDeltaCurrency,
        String description,
        UUID supersedesVersionId,
        String immutableHash,
        Instant createdAt
) {
}

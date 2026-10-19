package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Endorsement projection. Every endorsement issued a policy version. */
public record EndorsementDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        UUID policyVersionId,
        String endorsementNumber,
        int sequence,
        String formNumber,
        String formEdition,
        String endorsementType,
        LocalDate effectiveDate,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premiumDeltaAmount,
        String premiumDeltaCurrency,
        String coverageImpact,
        List<EndorsementCoverageChangeDto> coverageChanges,
        boolean manuscriptFlag,
        String backdateReason,
        UUID approvalRef,
        UUID approvedBy,
        String status,
        Instant issuedAt,
        Instant createdAt
) {
}

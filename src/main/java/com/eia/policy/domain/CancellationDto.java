package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cancellation projection with the computed return premium. */
public record CancellationDto(
        UUID id,
        UUID tenantId,
        UUID policyId,
        UUID policyVersionId,
        String cancellationType,
        String reason,
        LocalDate effectiveDate,
        int noticeDays,
        Instant noticedAt,
        int termDays,
        int remainingDays,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal proRataPct,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal earnedPremiumAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal returnPremiumAmount,
        String premiumCurrency,
        UUID approvalRef,
        UUID approvedBy,
        String status,
        Instant createdAt,
        String cancellationNumber
) {
}

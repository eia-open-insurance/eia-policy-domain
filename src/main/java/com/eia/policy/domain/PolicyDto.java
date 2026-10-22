package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyDto(
        UUID id,
        UUID tenantId,
        String policyNumber,
        String productCode,
        UUID insuredPartyId,
        String status,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premiumAmount,
        String premiumCurrency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limitAmount,
        String limitCurrency,
        int version,
        Instant createdAt,
        Instant updatedAt,
        UUID renewedFromPolicyId,
        UUID programId,
        UUID legalEntityId
) {}

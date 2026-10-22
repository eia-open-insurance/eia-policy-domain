package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of the premium-audit row the expert records when an audited
 * premium is applied. The expert owns the audit identity, the previous premium,
 * the derived delta, the status, and the creation instant; the domain accepts
 * the expert's {@code id} identity and SPEC-style column aliases while the
 * internal contract settles.
 *
 * <p>The public envelope carries this audit only when the expert supplies it;
 * a bare expert policy response is published as the {@link PolicyDto}
 * directly.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PremiumAuditDto(
        @JsonAlias({"id", "premiumAuditId", "premium_audit_id"}) UUID auditId,
        @JsonAlias("policy_id") UUID policyId,
        @JsonAlias({"auditedPremiumAmount", "audited_premium", "audited_premium_amount"})
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal auditedPremium,
        @JsonAlias({"previousPremiumAmount", "priorPremium", "previous_premium"})
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal previousPremium,
        @JsonAlias({"premiumDeltaAmount", "additionalPremium", "premium_delta"})
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premiumDelta,
        @JsonAlias({"premiumCurrency", "premium_currency"}) String currency,
        @JsonAlias({"auditDate", "effective_date"}) LocalDate effectiveDate,
        String reason,
        @JsonAlias("approved_by") UUID approvedBy,
        @JsonAlias("approval_ref") UUID approvalRef,
        String status,
        @JsonAlias({"created_at", "auditedAt"}) Instant createdAt
) {
}

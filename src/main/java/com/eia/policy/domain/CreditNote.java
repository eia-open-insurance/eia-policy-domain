package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One credit note requested against a direct policy (SPEC 11.2.13 billing and
 * receivables). The Policy expert owns the note number, the DRAFT/ISSUED/
 * APPLIED/CANCELLED lifecycle, the currency match, the approval gate, the SAP
 * credit-memo reference, and the status transitions; the domain publishes the
 * flat projection the billing screen renders.
 *
 * <p>{@code issuedAt} is set when the note reaches {@code ISSUED} and
 * {@code appliedAt} when it is applied to the receivable; both are absent until
 * that transition happens. {@code createdAt} is the request timestamp.
 * {@code reason} is optional and capped by the expert.</p>
 */
public record CreditNote(
        UUID creditNoteId,
        String noteNumber,
        UUID policyId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        String reason,
        String status,
        Instant issuedAt,
        Instant appliedAt,
        Instant createdAt
) {
}

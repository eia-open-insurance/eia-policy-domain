package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One credit note requested against a direct policy (SPEC 11.2.13 billing and
 * receivables). The Policy expert owns the note number, the DRAFT/ISSUED/
 * APPLIED/CANCELLED/REVERSED lifecycle, the currency match, the approval gate,
 * the SAP credit-memo reference, and the status transitions; the domain
 * publishes the flat projection the billing screen renders.
 *
 * <p>{@code issuedAt} is set when the note reaches {@code ISSUED} and
 * {@code appliedAt} when it is applied to the receivable; both are absent until
 * that transition happens. {@code appliedInstallmentId} identifies the open
 * installment the credit was posted against and is absent until the note is
 * {@code APPLIED}. A {@code REVERSED} note keeps its application facts so the
 * original credit stays auditable, and carries {@code reversedAt} plus the
 * {@code reversalReason} written by the reversal. {@code createdAt} is
 * the request timestamp. {@code reason} is optional and capped by the
 * expert.</p>
 *
 * <p>{@code cancellationId} links the note to the cancellation whose return
 * premium it credits and is absent when the note was raised for an endorsement,
 * audit, or another adjustment. {@code cancellationNumber} is the expert's
 * human-readable cancellation reference for a linked note and is absent
 * otherwise.</p>
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
        Instant createdAt,
        UUID appliedInstallmentId,
        Instant reversedAt,
        String reversalReason,
        UUID cancellationId,
        String cancellationNumber
) {
    /** Compatibility constructor for projections that predate credit application. */
    public CreditNote(UUID creditNoteId, String noteNumber, UUID policyId, BigDecimal amount,
                      String currency, String reason, String status, Instant issuedAt,
                      Instant appliedAt, Instant createdAt) {
        this(creditNoteId, noteNumber, policyId, amount, currency, reason, status, issuedAt,
                appliedAt, createdAt, null, null, null, null, null);
    }

    /** Compatibility constructor for projections that predate credit-note reversal. */
    public CreditNote(UUID creditNoteId, String noteNumber, UUID policyId, BigDecimal amount,
                      String currency, String reason, String status, Instant issuedAt,
                      Instant appliedAt, Instant createdAt, UUID appliedInstallmentId) {
        this(creditNoteId, noteNumber, policyId, amount, currency, reason, status, issuedAt,
                appliedAt, createdAt, appliedInstallmentId, null, null, null, null);
    }

    /**
     * Convenience constructor with the applied installment beside the applied
     * timestamp, the order used by the expert projection.
     */
    public CreditNote(UUID creditNoteId, String noteNumber, UUID policyId, BigDecimal amount,
                      String currency, String reason, String status, Instant issuedAt,
                      Instant appliedAt, UUID appliedInstallmentId, Instant createdAt) {
        this(creditNoteId, noteNumber, policyId, amount, currency, reason, status, issuedAt,
                appliedAt, createdAt, appliedInstallmentId, null, null, null, null);
    }

    /**
     * Convenience constructor with the full expert order: the applied
     * installment before the request timestamp, followed by the reversal facts.
     */
    public CreditNote(UUID creditNoteId, String noteNumber, UUID policyId, BigDecimal amount,
                      String currency, String reason, String status, Instant issuedAt,
                      Instant appliedAt, UUID appliedInstallmentId, Instant createdAt,
                      Instant reversedAt, String reversalReason) {
        this(creditNoteId, noteNumber, policyId, amount, currency, reason, status, issuedAt,
                appliedAt, createdAt, appliedInstallmentId, reversedAt, reversalReason,
                null, null);
    }

    /**
     * Convenience constructor with the full expert order and the cancellation
     * link appended after the reversal facts.
     */
    public CreditNote(UUID creditNoteId, String noteNumber, UUID policyId, BigDecimal amount,
                      String currency, String reason, String status, Instant issuedAt,
                      Instant appliedAt, UUID appliedInstallmentId, Instant createdAt,
                      Instant reversedAt, String reversalReason, UUID cancellationId,
                      String cancellationNumber) {
        this(creditNoteId, noteNumber, policyId, amount, currency, reason, status, issuedAt,
                appliedAt, createdAt, appliedInstallmentId, reversedAt, reversalReason,
                cancellationId, cancellationNumber);
    }
}

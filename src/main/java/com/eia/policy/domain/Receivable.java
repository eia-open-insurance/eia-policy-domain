package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One premium receivable: a scheduled installment enriched with the billing
 * plan, policy, and insured-party context needed by a collections view
 * (SPEC 11.2.13 {@code premium_receivable}).
 *
 * <p>{@code amount} stays the gross scheduled premium. {@code creditedAmount}
 * is the premium covered by applied credit notes and {@code outstandingAmount}
 * is the collectible remainder ({@code amount - creditedAmount}). The
 * collection totals use the outstanding amount so credits reduce what the
 * queue expects to collect.</p>
 *
 * <p>{@code daysPastDue} is zero unless the installment is past its due date.
 * {@code paidAt} and {@code paymentId} are present only after the installment
 * is paid. {@code lastDunningLevel} and {@code lastDunningAt} carry the most
 * recent dunning action recorded for the installment and are absent until one
 * is recorded.</p>
 */
public record Receivable(
        UUID installmentId,
        UUID planId,
        String planNumber,
        UUID policyId,
        String policyNumber,
        UUID insuredPartyId,
        int installmentNumber,
        LocalDate dueDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        String status,
        long daysPastDue,
        Instant paidAt,
        UUID paymentId,
        String lastDunningLevel,
        Instant lastDunningAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal creditedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingAmount
) {
    public Receivable {
        creditedAmount = creditedAmount == null ? BigDecimal.ZERO : creditedAmount;
        outstandingAmount = outstandingAmount == null
                ? outstanding(amount, creditedAmount)
                : outstandingAmount;
    }

    /** Compatibility constructor for receivables projected before credit application. */
    public Receivable(UUID installmentId, UUID planId, String planNumber, UUID policyId,
                      String policyNumber, UUID insuredPartyId, int installmentNumber,
                      LocalDate dueDate, BigDecimal amount, String currency, String status,
                      long daysPastDue, Instant paidAt, UUID paymentId, String lastDunningLevel,
                      Instant lastDunningAt) {
        this(installmentId, planId, planNumber, policyId, policyNumber, insuredPartyId,
                installmentNumber, dueDate, amount, currency, status, daysPastDue, paidAt,
                paymentId, lastDunningLevel, lastDunningAt, BigDecimal.ZERO, null);
    }

    /** Convenience constructor with the credit amounts beside the gross amount. */
    public Receivable(UUID installmentId, UUID planId, String planNumber, UUID policyId,
                      String policyNumber, UUID insuredPartyId, int installmentNumber,
                      LocalDate dueDate, BigDecimal amount, BigDecimal creditedAmount,
                      BigDecimal outstandingAmount, String currency, String status,
                      long daysPastDue, Instant paidAt, UUID paymentId, String lastDunningLevel,
                      Instant lastDunningAt) {
        this(installmentId, planId, planNumber, policyId, policyNumber, insuredPartyId,
                installmentNumber, dueDate, amount, currency, status, daysPastDue, paidAt,
                paymentId, lastDunningLevel, lastDunningAt, creditedAmount, outstandingAmount);
    }

    private static BigDecimal outstanding(BigDecimal amount, BigDecimal creditedAmount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = amount.subtract(creditedAmount);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}

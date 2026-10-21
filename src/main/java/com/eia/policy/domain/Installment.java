package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One premium installment in a policy billing plan. {@code paidAt} and
 * {@code paymentId} are present only after the installment is paid.
 *
 * <p>{@code creditedAmount} is the premium already covered by applied credit
 * notes and {@code outstandingAmount} is the collectible remainder (SPEC
 * 11.2.13 {@code BIL-06}). A PAID, WAIVED, or fully CREDITED installment has no
 * outstanding amount; a partially credited DUE/OVERDUE installment keeps the
 * difference.</p>
 */
public record Installment(
        UUID installmentId,
        int installmentNumber,
        LocalDate dueDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String status,
        Instant paidAt,
        UUID paymentId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal creditedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingAmount
) {
    public Installment {
        creditedAmount = creditedAmount == null ? BigDecimal.ZERO : creditedAmount;
        outstandingAmount = outstandingAmount == null
                ? BillingSummary.outstanding(amount, creditedAmount, status)
                : outstandingAmount;
    }

    /** Compatibility constructor for installments that predate credit application. */
    public Installment(UUID installmentId, int installmentNumber, LocalDate dueDate,
                       BigDecimal amount, String status, Instant paidAt, UUID paymentId) {
        this(installmentId, installmentNumber, dueDate, amount, status, paidAt, paymentId,
                BigDecimal.ZERO, null);
    }

    /**
     * Convenience constructor with the credit amounts beside the scheduled
     * amount, the order used by the billing API projection.
     */
    public Installment(UUID installmentId, int installmentNumber, LocalDate dueDate,
                       BigDecimal amount, BigDecimal creditedAmount,
                       BigDecimal outstandingAmount, String status, Instant paidAt,
                       UUID paymentId) {
        this(installmentId, installmentNumber, dueDate, amount, status, paidAt, paymentId,
                creditedAmount, outstandingAmount);
    }
}

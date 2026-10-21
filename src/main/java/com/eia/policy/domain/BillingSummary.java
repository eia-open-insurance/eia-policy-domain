package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Money summary of a billing plan, returned with the plan read. The plan total
 * is split across the installments; {@code paidAmount} and {@code waivedAmount}
 * are the settled portions and {@code creditedAmount} is the sum of every
 * credit note applied to the schedule.
 *
 * <p>{@code outstandingAmount} is the sum of each installment's outstanding
 * amount, so WAIVED, PAID, and fully CREDITED rows never contribute. It is the
 * {@code totalAmount - paidAmount - waivedAmount - creditedAmount} remainder
 * clamped at zero when an installment was settled after a partial credit.</p>
 */
public record BillingSummary(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal waivedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal creditedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdueAmount,
        String currency,
        int installmentCount
) {
    private static final String PAID = "PAID";
    private static final String WAIVED = "WAIVED";
    private static final String CREDITED = "CREDITED";
    private static final String OVERDUE = "OVERDUE";

    public BillingSummary {
        totalAmount = money(totalAmount);
        paidAmount = money(paidAmount);
        waivedAmount = money(waivedAmount);
        creditedAmount = money(creditedAmount);
        outstandingAmount = money(outstandingAmount);
        overdueAmount = money(overdueAmount);
        currency = currency == null || currency.isBlank() ? null : currency;
    }

    /** Derives the summary from a plan total and its installment schedule. */
    static BillingSummary of(BigDecimal totalAmount, String currency,
                             List<Installment> installments) {
        List<Installment> items = installments == null ? List.of() : installments;
        BigDecimal total = totalAmount == null ? sum(items, null, false) : totalAmount;
        BigDecimal paid = sum(items, PAID, false);
        BigDecimal waived = sum(items, WAIVED, false);
        BigDecimal credited = sum(items, null, true);
        BigDecimal outstanding = items.stream()
                .map(item -> item.outstandingAmount() == null
                        ? BigDecimal.ZERO : item.outstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = items.stream()
                .filter(item -> OVERDUE.equalsIgnoreCase(item.status()))
                .map(item -> item.outstandingAmount() == null
                        ? BigDecimal.ZERO : item.outstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BillingSummary(total, paid, waived, credited, outstanding, overdue,
                currency, items.size());
    }

    /**
     * The collectible remainder of one installment. Settled and fully credited
     * installments have nothing left to collect; an open installment keeps the
     * difference between its scheduled amount and applied credits.
     */
    static BigDecimal outstanding(BigDecimal amount, BigDecimal creditedAmount, String status) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (isSettled(status)) {
            return BigDecimal.ZERO;
        }
        BigDecimal credited = creditedAmount == null ? BigDecimal.ZERO : creditedAmount;
        BigDecimal remaining = amount.subtract(credited);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    private static boolean isSettled(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return PAID.equals(normalized) || WAIVED.equals(normalized) || CREDITED.equals(normalized);
    }

    private static BigDecimal sum(List<Installment> items, String status, boolean credited) {
        return items.stream()
                .filter(item -> status == null
                        || status.equalsIgnoreCase(item.status()))
                .map(item -> credited ? item.creditedAmount() : item.amount())
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

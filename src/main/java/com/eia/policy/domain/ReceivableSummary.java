package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tenant-scoped premium-receivable collection: the matching installments plus
 * their totals. {@code totalAmount} is the sum of every returned receivable;
 * {@code overdueAmount} is the sum of the receivables past their due date.
 * {@code count} is the number of matching receivables returned by the expert
 * (normally the item count).
 */
public record ReceivableSummary(
        List<Receivable> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdueAmount,
        int count
) {
    public ReceivableSummary {
        items = items == null ? List.of() : List.copyOf(items);
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        overdueAmount = overdueAmount == null ? BigDecimal.ZERO : overdueAmount;
    }
}

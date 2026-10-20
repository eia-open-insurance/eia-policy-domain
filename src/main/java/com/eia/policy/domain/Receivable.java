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
 * <p>{@code daysPastDue} is zero for settled installments and for an
 * installment whose due date has not passed. {@code paidAt} and
 * {@code paymentId} are present only after the installment is paid.</p>
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
        int daysPastDue,
        Instant paidAt,
        UUID paymentId
) {
}

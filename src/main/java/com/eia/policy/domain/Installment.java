package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One premium installment in a policy billing plan. {@code paidAt} and
 * {@code paymentId} are present only after the installment is paid.
 */
public record Installment(
        UUID installmentId,
        int installmentNumber,
        LocalDate dueDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String status,
        Instant paidAt,
        UUID paymentId
) {
}

package com.eia.policy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSummaryTest {
    @Test
    void netsPaidWaivedAndCreditedPremiumOutOfTheOutstandingTotal() {
        BillingSummary summary = BillingSummary.of(new BigDecimal("300.0000"), "USD", List.of(
                installment("PAID", "100.0000", "0.0000", "0.0000"),
                installment("WAIVED", "100.0000", "0.0000", "0.0000"),
                installment("DUE", "100.0000", "40.0000", "60.0000")));

        assertThat(summary.totalAmount()).isEqualByComparingTo("300.0000");
        assertThat(summary.paidAmount()).isEqualByComparingTo("100.0000");
        assertThat(summary.waivedAmount()).isEqualByComparingTo("100.0000");
        assertThat(summary.creditedAmount()).isEqualByComparingTo("40.0000");
        assertThat(summary.outstandingAmount()).isEqualByComparingTo("60.0000");
        assertThat(summary.installmentCount()).isEqualTo(3);
    }

    @Test
    void settledAndFullyCreditedInstallmentsHaveNoOutstandingAmount() {
        assertThat(BillingSummary.outstanding(new BigDecimal("100.0000"),
                new BigDecimal("40.0000"), "DUE")).isEqualByComparingTo("60.0000");
        assertThat(BillingSummary.outstanding(new BigDecimal("100.0000"),
                new BigDecimal("100.0000"), "CREDITED")).isEqualByComparingTo("0");
        assertThat(BillingSummary.outstanding(new BigDecimal("100.0000"),
                new BigDecimal("40.0000"), "PAID")).isEqualByComparingTo("0");
        assertThat(BillingSummary.outstanding(new BigDecimal("100.0000"),
                new BigDecimal("40.0000"), "WAIVED")).isEqualByComparingTo("0");
    }

    private static Installment installment(String status, String amount, String credited,
                                           String outstanding) {
        return new Installment(UUID.randomUUID(), 1, LocalDate.of(2026, 1, 15),
                new BigDecimal(amount), status, null, null, new BigDecimal(credited),
                new BigDecimal(outstanding));
    }
}

package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyBillingController.class)
class PolicyBillingControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PAYMENT_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String DIRECT = "DIRECT";
    private static final String BILLING = "/api/v1/policies/{policyId}/billing";
    private static final String ADJUSTMENT = BILLING + "/adjustment";
    private static final BillingAdjustmentCommand ADJUSTMENT_COMMAND =
            new BillingAdjustmentCommand(LocalDate.of(2026, 5, 1), "exposure audit");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyBillingService service;

    @Test
    void returnsTheBillingPlanWithItsInstallments() throws Exception {
        when(service.get(TENANT, POLICY_ID)).thenReturn(billingPlan());

        mvc.perform(get(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.planNumber").value("BP-2026-0001"))
                .andExpect(jsonPath("$.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.totalPremium").value("12000.0000"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.installmentCount").value(2))
                .andExpect(jsonPath("$.frequency").value("SEMI_ANNUAL"))
                .andExpect(jsonPath("$.firstDueDate").value("2026-01-15"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.installments.length()").value(2))
                .andExpect(jsonPath("$.installments[0].installmentId")
                        .value(INSTALLMENT_ID.toString()))
                .andExpect(jsonPath("$.installments[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-01-15"))
                .andExpect(jsonPath("$.installments[0].amount").value("6000.0000"))
                .andExpect(jsonPath("$.installments[0].creditedAmount").value("0.0000"))
                .andExpect(jsonPath("$.installments[0].outstandingAmount").value("0.0000"))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.installments[0].paidAt").value("2026-01-15T12:00:00Z"))
                .andExpect(jsonPath("$.installments[0].paymentId")
                        .value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.installments[1].creditedAmount").value("1000.0000"))
                .andExpect(jsonPath("$.installments[1].outstandingAmount").value("5000.0000"))
                .andExpect(jsonPath("$.installments[1].status").value("PENDING"))
                .andExpect(jsonPath("$.summary.totalAmount").value("12000.0000"))
                .andExpect(jsonPath("$.summary.paidAmount").value("6000.0000"))
                .andExpect(jsonPath("$.summary.creditedAmount").value("1000.0000"))
                .andExpect(jsonPath("$.summary.outstandingAmount").value("5000.0000"))
                .andExpect(jsonPath("$.summary.currency").value("USD"))
                .andExpect(jsonPath("$.summary.installmentCount").value(2));
    }

    @Test
    void createsTheBillingPlanAndReturnsCreated() throws Exception {
        CreateBillingPlanCommand command = new CreateBillingPlanCommand(2, "SEMI_ANNUAL",
                LocalDate.of(2026, 1, 15));
        when(service.create(TENANT, POLICY_ID, command)).thenReturn(billingPlan());

        mvc.perform(post(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installmentCount": 2, "frequency": "SEMI_ANNUAL",
                                 "firstDueDate": "2026-01-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planNumber").value("BP-2026-0001"))
                .andExpect(jsonPath("$.installments.length()").value(2));

        verify(service).create(TENANT, POLICY_ID, command);
    }

    @Test
    void updatesAnInstallmentAndReturnsIt() throws Exception {
        UpdateInstallmentCommand command = new UpdateInstallmentCommand("PAID", PAYMENT_ID);
        when(service.updateInstallment(TENANT, POLICY_ID, INSTALLMENT_ID, command))
                .thenReturn(installment());

        mvc.perform(patch(BILLING + "/installments/{installmentId}", POLICY_ID, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAID", "paymentId": "%s"}
                                """.formatted(PAYMENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentId").value(INSTALLMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").value("2026-01-15T12:00:00Z"))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()));

        verify(service).updateInstallment(TENANT, POLICY_ID, INSTALLMENT_ID, command);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.get(TENANT, POLICY_ID)).thenReturn(billingPlan());

        mvc.perform(get(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planNumber").value("BP-2026-0001"));
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(service.get(TENANT, POLICY_ID)).thenReturn(billingPlan());

        mvc.perform(get(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(BILLING, POLICY_ID)
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheCreateBillingPlanBody() throws Exception {
        mvc.perform(post(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("installmentCount", "frequency", "firstDueDate")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANonPositiveInstallmentCount() throws Exception {
        mvc.perform(post(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installmentCount": 0, "frequency": "MONTHLY",
                                 "firstDueDate": "2026-01-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("installmentCount")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMoreThanTwentyFourInstallments() throws Exception {
        mvc.perform(post(BILLING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installmentCount": 25, "frequency": "MONTHLY",
                                 "firstDueDate": "2026-01-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("installmentCount")));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheInstallmentUpdateBody() throws Exception {
        mvc.perform(patch(BILLING + "/installments/{installmentId}", POLICY_ID, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("status")));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.updateInstallment(eq(TENANT), eq(POLICY_ID), eq(INSTALLMENT_ID),
                any(UpdateInstallmentCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "INSTALLMENT_ALREADY_SETTLED",
                        "Installment 1 is already PAID",
                        List.of(new ApiException.FieldIssue("status", "INVALID_TRANSITION",
                                "PAID cannot transition to PAID"))));

        mvc.perform(patch(BILLING + "/installments/{installmentId}", POLICY_ID, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAID"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSTALLMENT_ALREADY_SETTLED"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TRANSITION"));
    }

    @Test
    void appendsTheAdjustmentAndReturnsTheEnvelope() throws Exception {
        when(service.adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, "\"4\"", "adjust-key-1"))
                .thenReturn(new Mutation<>(adjustmentResult(), "\"5\"", false));

        mvc.perform(adjustmentRequest()
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "adjust-key-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.plan.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.plan.planNumber").value("BP-2026-0001"))
                .andExpect(jsonPath("$.plan.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.plan.totalPremium").value("150000.0000"))
                .andExpect(jsonPath("$.plan.currency").value("USD"))
                .andExpect(jsonPath("$.plan.installmentCount").value(4))
                .andExpect(jsonPath("$.plan.frequency").value("QUARTERLY"))
                .andExpect(jsonPath("$.plan.firstDueDate").value("2026-01-15"))
                .andExpect(jsonPath("$.plan.status").value("ACTIVE"))
                .andExpect(jsonPath("$.installment.installmentId")
                        .value(INSTALLMENT_ID.toString()))
                .andExpect(jsonPath("$.installment.installmentNumber").value(4))
                .andExpect(jsonPath("$.installment.dueDate").value("2026-05-01"))
                .andExpect(jsonPath("$.installment.amount").value("30000.0000"))
                .andExpect(jsonPath("$.installment.creditedAmount").value("0.0000"))
                .andExpect(jsonPath("$.installment.outstandingAmount").value("30000.0000"))
                .andExpect(jsonPath("$.installment.status").value("DUE"))
                .andExpect(jsonPath("$.adjustmentAmount").value("30000.0000"))
                .andExpect(jsonPath("$.newTotalPremium").value("150000.0000"));

        verify(service).adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, "\"4\"", "adjust-key-1");
    }

    @Test
    void returnsOkWhenTheExpertReplaysTheAdjustment() throws Exception {
        when(service.adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, null, "adjust-key-1"))
                .thenReturn(new Mutation<>(adjustmentResult(), "\"5\"", true));

        mvc.perform(adjustmentRequest().header("Idempotency-Key", "adjust-key-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.adjustmentAmount").value("30000.0000"));

        verify(service).adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, null, "adjust-key-1");
    }

    @Test
    void acceptsAnAbsentBookForTheAdjustment() throws Exception {
        when(service.adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, null, null))
                .thenReturn(new Mutation<>(adjustmentResult(), null, false));

        mvc.perform(post(ADJUSTMENT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueDate": "2026-05-01", "reason": "exposure audit"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.plan.planNumber").value("BP-2026-0001"));
    }

    @Test
    void rejectsAnyOtherBookForTheAdjustment() throws Exception {
        mvc.perform(adjustmentRequest().header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheAdjustmentBody() throws Exception {
        mvc.perform(post(ADJUSTMENT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("dueDate", "reason")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsABlankAdjustmentReason() throws Exception {
        mvc.perform(post(ADJUSTMENT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueDate": "2026-05-01", "reason": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("reason")));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertNoPremiumAdjustmentProblem() throws Exception {
        when(service.adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "NO_PREMIUM_ADJUSTMENT",
                        "The policy premium does not exceed the billing plan total.",
                        List.of(new ApiException.FieldIssue("delta", "NO_PREMIUM_ADJUSTMENT",
                                "A premium decrease must use a credit note.")),
                        Map.of("delta", "0.0000")));

        mvc.perform(adjustmentRequest())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NO_PREMIUM_ADJUSTMENT"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("delta"))
                .andExpect(jsonPath("$.meta.delta").value("0.0000"));
    }

    @Test
    void preservesTheMissingBillingPlanProblem() throws Exception {
        when(service.adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "BILLING_PLAN_NOT_FOUND",
                        "No billing plan exists for policy " + POLICY_ID,
                        List.of(new ApiException.FieldIssue("policyId", "BILLING_PLAN_NOT_FOUND",
                                "No billing plan exists for policy " + POLICY_ID))));

        mvc.perform(adjustmentRequest())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BILLING_PLAN_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].field").value("policyId"));
    }

    @Test
    void preservesTheInactivePlanConflictProblem() throws Exception {
        when(service.adjust(TENANT, POLICY_ID, ADJUSTMENT_COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "BILLING_PLAN_NOT_ACTIVE",
                        "Billing plan " + PLAN_ID + " is CANCELLED; only an ACTIVE plan can be adjusted.",
                        List.of(new ApiException.FieldIssue("policyId", "BILLING_PLAN_NOT_ACTIVE",
                                "Only an ACTIVE plan can be adjusted.")),
                        Map.of("planId", PLAN_ID.toString(), "currentStatus", "CANCELLED")));

        mvc.perform(adjustmentRequest())
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BILLING_PLAN_NOT_ACTIVE"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.meta.currentStatus").value("CANCELLED"));
    }

    private static MockHttpServletRequestBuilder adjustmentRequest() {
        return post(ADJUSTMENT, POLICY_ID)
                .header("X-EIA-Tenant", TENANT.toString())
                .header("X-EIA-Book", DIRECT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"dueDate": "2026-05-01", "reason": "exposure audit"}
                        """);
    }

    private static BillingAdjustmentResult adjustmentResult() {
        BillingPlan plan = new BillingPlan(PLAN_ID, "BP-2026-0001", POLICY_ID,
                new BigDecimal("150000.0000"), "USD", 4, "QUARTERLY",
                LocalDate.of(2026, 1, 15), "ACTIVE", List.of());
        Installment installment = new Installment(INSTALLMENT_ID, 4,
                LocalDate.of(2026, 5, 1), new BigDecimal("30000.0000"),
                new BigDecimal("0.0000"), new BigDecimal("30000.0000"), "DUE", null, null);
        return new BillingAdjustmentResult(plan, installment, new BigDecimal("30000.0000"),
                new BigDecimal("150000.0000"));
    }

    private static BillingPlan billingPlan() {
        return new BillingPlan(PLAN_ID, "BP-2026-0001", POLICY_ID,
                new BigDecimal("12000.0000"), "USD", 2, "SEMI_ANNUAL",
                LocalDate.of(2026, 1, 15), "ACTIVE",
                List.of(installment(), pendingInstallment()));
    }

    private static Installment installment() {
        return new Installment(INSTALLMENT_ID, 1, LocalDate.of(2026, 1, 15),
                new BigDecimal("6000.0000"), new BigDecimal("0.0000"),
                new BigDecimal("0.0000"), "PAID",
                Instant.parse("2026-01-15T12:00:00Z"), PAYMENT_ID);
    }

    private static Installment pendingInstallment() {
        return new Installment(UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd"), 2,
                LocalDate.of(2026, 7, 15), new BigDecimal("6000.0000"),
                new BigDecimal("1000.0000"), new BigDecimal("5000.0000"), "PENDING",
                null, null);
    }
}

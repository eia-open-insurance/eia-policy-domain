package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

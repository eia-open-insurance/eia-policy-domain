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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceivableController.class)
class PolicyReceivableControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID INSURED_PARTY_ID =
            UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final String PATH = "/api/v1/billing/installments";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReceivableService service;

    @Test
    void returnsTheReceivableSummary() throws Exception {
        when(service.list(TENANT, "OVERDUE", LocalDate.of(2026, 2, 1), 5))
                .thenReturn(summary());

        mvc.perform(get(PATH)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .param("status", "OVERDUE")
                        .param("dueBefore", "2026-02-01")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value("12000.0000"))
                .andExpect(jsonPath("$.overdueAmount").value("12000.0000"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].installmentId")
                        .value(INSTALLMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.items[0].planNumber").value("BP-2026-0001"))
                .andExpect(jsonPath("$.items[0].policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.items[0].policyNumber").value("POL-2026-0000001"))
                .andExpect(jsonPath("$.items[0].insuredPartyId")
                        .value(INSURED_PARTY_ID.toString()))
                .andExpect(jsonPath("$.items[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-01-15"))
                .andExpect(jsonPath("$.items[0].amount").value("6000.0000"))
                .andExpect(jsonPath("$.items[0].currency").value("USD"))
                .andExpect(jsonPath("$.items[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.items[0].daysPastDue").value(17))
                .andExpect(jsonPath("$.items[0].lastDunningLevel").value("DUNNING_1"))
                .andExpect(jsonPath("$.items[0].lastDunningAt")
                        .value("2026-02-01T09:00:00Z"))
                .andExpect(jsonPath("$.items[1].lastDunningLevel").value(nullValue()));

        verify(service).list(TENANT, "OVERDUE", LocalDate.of(2026, 2, 1), 5);
    }

    @Test
    void passesAbsentFiltersAndBookForTheServiceToDefault() throws Exception {
        when(service.list(TENANT, null, null, null)).thenReturn(summary());

        mvc.perform(get(PATH).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        verify(service).list(TENANT, null, null, null);
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(service.list(TENANT, null, null, null)).thenReturn(summary());

        mvc.perform(get(PATH)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());

        verify(service).list(TENANT, null, null, null);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(PATH).header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(PATH)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMalformedDueBeforeBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(PATH)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("dueBefore", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("dueBefore"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANonNumericLimitBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(PATH)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("limit", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.list(TENANT, "SUNSET", null, null))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTALLMENT_STATUS",
                        "status must be one of DUE, OVERDUE, PAID, WAIVED",
                        List.of(new ApiException.FieldIssue("status", "INVALID_FORMAT",
                                "is not a known installment status"))));

        mvc.perform(get(PATH)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("status", "SUNSET"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_INSTALLMENT_STATUS"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.list(TENANT, null, null, null))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND",
                        "Policy not found: " + POLICY_ID));

        mvc.perform(get(PATH).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    private static ReceivableSummary summary() {
        return new ReceivableSummary(
                List.of(
                        new Receivable(INSTALLMENT_ID, PLAN_ID, "BP-2026-0001", POLICY_ID,
                                "POL-2026-0000001", INSURED_PARTY_ID, 1,
                                LocalDate.of(2026, 1, 15), new BigDecimal("6000.0000"), "USD",
                                "OVERDUE", 17, null, null, "DUNNING_1",
                                Instant.parse("2026-02-01T09:00:00Z")),
                        new Receivable(UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd"),
                                PLAN_ID, "BP-2026-0001", POLICY_ID, "POL-2026-0000001",
                                INSURED_PARTY_ID, 2, LocalDate.of(2026, 7, 15),
                                new BigDecimal("6000.0000"), "USD", "OVERDUE", 3, null, null,
                                null, null)),
                new BigDecimal("12000.0000"), new BigDecimal("12000.0000"), 2);
    }
}

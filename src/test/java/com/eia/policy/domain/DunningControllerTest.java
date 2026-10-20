package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DunningController.class)
class DunningControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID DUNNING_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final UUID SECOND_DUNNING_ID =
            UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final String PATH = "/api/v1/billing/installments/{installmentId}/dunning";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DunningService service;

    @Test
    void returnsTheDunningHistoryNewestFirst() throws Exception {
        when(service.list(TENANT, INSTALLMENT_ID)).thenReturn(List.of(
                action(SECOND_DUNNING_ID, "SECOND_NOTICE", "LETTER",
                        "Final notice before collections", "2026-07-01T09:00:00Z"),
                action(DUNNING_ID, "FIRST_NOTICE", "EMAIL",
                        "Lapse warning sent to the broker", "2026-06-15T12:00:00Z")));

        mvc.perform(get(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].dunningId").value(SECOND_DUNNING_ID.toString()))
                .andExpect(jsonPath("$[0].installmentId").value(INSTALLMENT_ID.toString()))
                .andExpect(jsonPath("$[0].planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$[0].policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$[0].level").value("SECOND_NOTICE"))
                .andExpect(jsonPath("$[0].channel").value("LETTER"))
                .andExpect(jsonPath("$[0].notes").value("Final notice before collections"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].sentAt").value("2026-07-01T09:00:00Z"))
                .andExpect(jsonPath("$[1].dunningId").value(DUNNING_ID.toString()))
                .andExpect(jsonPath("$[1].level").value("FIRST_NOTICE"));

        verify(service).list(TENANT, INSTALLMENT_ID);
    }

    @Test
    void returnsAnEmptyHistoryWhenNothingWasRecorded() throws Exception {
        when(service.list(TENANT, INSTALLMENT_ID)).thenReturn(List.of());

        mvc.perform(get(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recordsADunningActionAndReturnsCreated() throws Exception {
        CreateDunningActionCommand command = new CreateDunningActionCommand("FIRST_NOTICE", "EMAIL",
                "Lapse warning sent to the broker");
        when(service.record(TENANT, INSTALLMENT_ID, command))
                .thenReturn(action(DUNNING_ID, "FIRST_NOTICE", "EMAIL",
                        "Lapse warning sent to the broker", "2026-06-15T12:00:00Z"));

        mvc.perform(post(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level": "FIRST_NOTICE", "channel": "EMAIL",
                                 "notes": "Lapse warning sent to the broker"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dunningId").value(DUNNING_ID.toString()))
                .andExpect(jsonPath("$.level").value("FIRST_NOTICE"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("SENT"));

        verify(service).record(TENANT, INSTALLMENT_ID, command);
    }

    @Test
    void acceptsAnAbsentNotesField() throws Exception {
        CreateDunningActionCommand command = new CreateDunningActionCommand("REMINDER", "LETTER",
                null);
        when(service.record(TENANT, INSTALLMENT_ID, command))
                .thenReturn(action(DUNNING_ID, "REMINDER", "LETTER", null,
                        "2026-06-15T12:00:00Z"));

        mvc.perform(post(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level": "REMINDER", "channel": "LETTER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("LETTER"));

        verify(service).record(TENANT, INSTALLMENT_ID, command);
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(service.list(TENANT, INSTALLMENT_ID)).thenReturn(List.of());

        mvc.perform(get(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(PATH, INSTALLMENT_ID).header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level": "REMINDER", "channel": "EMAIL"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheDunningActionBody() throws Exception {
        mvc.perform(post(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("level", "channel")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMalformedInstallmentIdBeforeCallingTheExpert() throws Exception {
        mvc.perform(get("/api/v1/billing/installments/not-a-uuid/dunning")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("installmentId"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.record(eq(TENANT), eq(INSTALLMENT_ID),
                any(CreateDunningActionCommand.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DUNNING_LEVEL",
                        "level must be one of REMINDER, FIRST_NOTICE, SECOND_NOTICE, COLLECTIONS.",
                        List.of(new ApiException.FieldIssue("level", "INVALID_DUNNING_LEVEL",
                                "is not a known dunning level"))));

        mvc.perform(post(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level": "DUNNING_9", "channel": "EMAIL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DUNNING_LEVEL"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("level"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_DUNNING_LEVEL"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.list(TENANT, INSTALLMENT_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "INSTALLMENT_NOT_FOUND",
                        "Installment not found: " + INSTALLMENT_ID));

        mvc.perform(get(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.record(eq(TENANT), eq(INSTALLMENT_ID),
                any(CreateDunningActionCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "DUNNING_ALREADY_SENT",
                        "A REMINDER dunning action already exists for installment "
                                + INSTALLMENT_ID + ".",
                        List.of(new ApiException.FieldIssue("level", "DUNNING_ALREADY_SENT",
                                "A REMINDER dunning action already exists."))));

        mvc.perform(post(PATH, INSTALLMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level": "REMINDER", "channel": "LETTER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUNNING_ALREADY_SENT"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("level"));
    }

    private static DunningAction action(UUID dunningId, String level, String channel, String notes,
                                        String sentAt) {
        return new DunningAction(dunningId, INSTALLMENT_ID, PLAN_ID, POLICY_ID,
                level, channel, notes, "SENT", Instant.parse(sentAt));
    }
}

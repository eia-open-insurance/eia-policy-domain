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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
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

@WebMvcTest(PolicyCreditNoteController.class)
class PolicyCreditNoteControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID CREDIT_NOTE_ID =
            UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final UUID SECOND_CREDIT_NOTE_ID =
            UUID.fromString("ffffffff-ffff-7fff-8fff-ffffffffffff");
    private static final UUID INSTALLMENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final String NOTES = "/api/v1/policies/{policyId}/credit-notes";
    private static final String NOTE_STATUS = NOTES + "/{creditNoteId}/status";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyCreditNoteService service;

    @Test
    void returnsThePolicysCreditNotesNewestFirst() throws Exception {
        when(service.list(TENANT, POLICY_ID)).thenReturn(List.of(
                note(SECOND_CREDIT_NOTE_ID, "CN-2026-0008", "ENDORSEMENT", "APPLIED",
                        "2026-10-01T09:00:00Z", "2026-10-02T09:00:00Z",
                        "2026-10-05T09:00:00Z"),
                note(CREDIT_NOTE_ID, "CN-2026-0007", "CANCELLATION", "DRAFT",
                        "2026-10-20T18:00:00Z", null, null)));

        mvc.perform(get(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].creditNoteId").value(SECOND_CREDIT_NOTE_ID.toString()))
                .andExpect(jsonPath("$[0].noteNumber").value("CN-2026-0008"))
                .andExpect(jsonPath("$[0].policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$[0].amount").value("320.00"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].reason").value("ENDORSEMENT"))
                .andExpect(jsonPath("$[0].status").value("APPLIED"))
                .andExpect(jsonPath("$[0].issuedAt").value("2026-10-02T09:00:00Z"))
                .andExpect(jsonPath("$[0].appliedAt").value("2026-10-05T09:00:00Z"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-01T09:00:00Z"))
                .andExpect(jsonPath("$[1].creditNoteId").value(CREDIT_NOTE_ID.toString()))
                .andExpect(jsonPath("$[1].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].issuedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].appliedAt").value(nullValue()));

        verify(service).list(TENANT, POLICY_ID);
    }

    @Test
    void returnsAnEmptyListWhenThePolicyHasNoCreditNote() throws Exception {
        when(service.list(TENANT, POLICY_ID)).thenReturn(List.of());

        mvc.perform(get(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requestsACreditNoteAndReturnsCreated() throws Exception {
        CreateCreditNoteCommand command =
                new CreateCreditNoteCommand(new BigDecimal("1250.50"), "USD", "CANCELLATION");
        when(service.create(TENANT, POLICY_ID, command)).thenReturn(
                note(CREDIT_NOTE_ID, "CN-2026-0007", new BigDecimal("1250.5000"),
                        "CANCELLATION", "DRAFT", "2026-10-20T18:00:00Z", null, null));

        mvc.perform(post(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1250.50, "currency": "USD",
                                 "reason": "CANCELLATION"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditNoteId").value(CREDIT_NOTE_ID.toString()))
                .andExpect(jsonPath("$.noteNumber").value("CN-2026-0007"))
                .andExpect(jsonPath("$.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.amount").value("1250.5000"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.reason").value("CANCELLATION"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T18:00:00Z"));

        verify(service).create(TENANT, POLICY_ID, command);
    }

    @Test
    void acceptsAnAbsentReason() throws Exception {
        CreateCreditNoteCommand command =
                new CreateCreditNoteCommand(new BigDecimal("10.00"), "USD", null);
        when(service.create(TENANT, POLICY_ID, command)).thenReturn(
                note(CREDIT_NOTE_ID, "CN-2026-0007", new BigDecimal("10.0000"), null,
                        "DRAFT", "2026-10-20T18:00:00Z", null, null));

        mvc.perform(post(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "currency": "USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditNoteId").value(CREDIT_NOTE_ID.toString()))
                .andExpect(jsonPath("$.amount").value("10.0000"))
                .andExpect(jsonPath("$.reason").value(nullValue()));

        verify(service).create(TENANT, POLICY_ID, command);
    }

    @Test
    void advancesTheCreditNoteStatus() throws Exception {
        UpdateCreditNoteStatusCommand command = new UpdateCreditNoteStatusCommand("ISSUED");
        when(service.updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command)).thenReturn(
                note(CREDIT_NOTE_ID, "CN-2026-0007", "CANCELLATION", "ISSUED",
                        "2026-10-20T18:00:00Z", "2026-10-21T10:00:00Z", null));

        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ISSUED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNoteId").value(CREDIT_NOTE_ID.toString()))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt").value("2026-10-21T10:00:00Z"))
                .andExpect(jsonPath("$.appliedAt").value(nullValue()));

        verify(service).updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command);
    }

    @Test
    void appliesTheCreditNoteToTheNamedInstallment() throws Exception {
        UpdateCreditNoteStatusCommand command =
                new UpdateCreditNoteStatusCommand("APPLIED", INSTALLMENT_ID);
        when(service.updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command)).thenReturn(
                new CreditNote(CREDIT_NOTE_ID, "CN-2026-0007", POLICY_ID,
                        new BigDecimal("320.00"), "USD", "CANCELLATION", "APPLIED",
                        Instant.parse("2026-10-21T10:00:00Z"),
                        Instant.parse("2026-10-21T11:00:00Z"),
                        Instant.parse("2026-10-20T18:00:00Z"), INSTALLMENT_ID));

        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "APPLIED", "installmentId": "%s"}
                                """.formatted(INSTALLMENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedAt").value("2026-10-21T11:00:00Z"))
                .andExpect(jsonPath("$.appliedInstallmentId")
                        .value(INSTALLMENT_ID.toString()));

        verify(service).updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command);
    }

    @Test
    void reversesTheCreditNoteWithAReason() throws Exception {
        UpdateCreditNoteStatusCommand command =
                new UpdateCreditNoteStatusCommand("REVERSED", null, "REFUND_ISSUED");
        when(service.updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command)).thenReturn(
                new CreditNote(CREDIT_NOTE_ID, "CN-2026-0007", POLICY_ID,
                        new BigDecimal("320.00"), "USD", "CANCELLATION", "REVERSED",
                        Instant.parse("2026-10-21T10:00:00Z"),
                        Instant.parse("2026-10-21T11:00:00Z"),
                        Instant.parse("2026-10-20T18:00:00Z"), INSTALLMENT_ID,
                        Instant.parse("2026-10-22T08:00:00Z"), "REFUND_ISSUED"));

        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "REVERSED", "reason": "REFUND_ISSUED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.appliedAt").value("2026-10-21T11:00:00Z"))
                .andExpect(jsonPath("$.appliedInstallmentId")
                        .value(INSTALLMENT_ID.toString()))
                .andExpect(jsonPath("$.reversedAt").value("2026-10-22T08:00:00Z"))
                .andExpect(jsonPath("$.reversalReason").value("REFUND_ISSUED"));

        verify(service).updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command);
    }

    @Test
    void acceptsTheDirectBookCaseInsensitivelyAndAnAbsentBook() throws Exception {
        when(service.list(TENANT, POLICY_ID)).thenReturn(List.of());

        mvc.perform(get(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());

        mvc.perform(get(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(NOTES, POLICY_ID).header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "currency": "USD", "reason": "REFUND"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheCreditNoteRequestShape() throws Exception {
        mvc.perform(post(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("amount", "currency")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANonPositiveAmountAndAMalformedCurrency() throws Exception {
        mvc.perform(post(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0, "currency": "US", "reason": "REFUND"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("amount", "currency")));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheCreditNoteStatusBody() throws Exception {
        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAReversalReasonLongerThanFiveHundredCharacters() throws Exception {
        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "REVERSED", "reason": "%s"}
                                """.formatted("x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("reason")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedPolicyAndCreditNoteIdsBeforeCallingTheExpert() throws Exception {
        mvc.perform(get("/api/v1/policies/not-a-uuid/credit-notes")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("policyId"));

        mvc.perform(patch("/api/v1/policies/{policyId}/credit-notes/not-a-uuid/status",
                        POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ISSUED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("creditNoteId"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), eq(POLICY_ID), any(CreateCreditNoteCommand.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDIT_NOTE",
                        "reason must be one of CANCELLATION, ENDORSEMENT, AUDIT_RETURN, "
                                + "OVERPAYMENT, REFUND, CORRECTION.",
                        List.of(new ApiException.FieldIssue("reason", "INVALID_CREDIT_NOTE",
                                "is not a known credit-note reason"))));

        mvc.perform(post(NOTES, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "currency": "USD", "reason": "GOODWILL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDIT_NOTE"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDIT_NOTE"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.updateStatus(eq(TENANT), eq(POLICY_ID), eq(CREDIT_NOTE_ID),
                any(UpdateCreditNoteStatusCommand.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "CREDIT_NOTE_NOT_FOUND",
                        "Credit note not found: " + CREDIT_NOTE_ID));

        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "APPROVED"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CREDIT_NOTE_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.updateStatus(eq(TENANT), eq(POLICY_ID), eq(CREDIT_NOTE_ID),
                any(UpdateCreditNoteStatusCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT,
                        "INVALID_CREDIT_NOTE_TRANSITION",
                        "A DRAFT credit note cannot move to APPLIED.",
                        List.of(new ApiException.FieldIssue("status",
                                "INVALID_CREDIT_NOTE_TRANSITION",
                                "transition is not allowed")),
                        java.util.Map.of("currentStatus", "DRAFT",
                                "targetStatus", "APPLIED")));

        mvc.perform(patch(NOTE_STATUS, POLICY_ID, CREDIT_NOTE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "APPLIED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDIT_NOTE_TRANSITION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.meta.currentStatus").value("DRAFT"));
    }

    private static CreditNote note(UUID creditNoteId, String noteNumber, String reason,
                                   String status, String createdAt, String issuedAt,
                                   String appliedAt) {
        return note(creditNoteId, noteNumber, new BigDecimal("320.00"), reason, status,
                createdAt, issuedAt, appliedAt);
    }

    private static CreditNote note(UUID creditNoteId, String noteNumber, BigDecimal amount,
                                   String reason, String status, String createdAt, String issuedAt,
                                   String appliedAt) {
        return new CreditNote(creditNoteId, noteNumber, POLICY_ID, amount, "USD", reason, status,
                issuedAt == null ? null : Instant.parse(issuedAt),
                appliedAt == null ? null : Instant.parse(appliedAt), Instant.parse(createdAt));
    }
}

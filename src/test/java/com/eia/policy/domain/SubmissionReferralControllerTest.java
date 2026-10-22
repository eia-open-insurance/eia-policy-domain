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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
class SubmissionReferralControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final String DIRECT = "DIRECT";
    private static final String BASE = "/api/v1/policies/submissions";
    private static final String REFERRAL = BASE + "/{submissionId}/referral";
    private static final String DECISION = BASE + "/{submissionId}/referral/decision";
    private static final Instant CREATED = Instant.parse("2026-10-22T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-22T10:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-22T12:00:00Z");

    private static final ReferSubmissionCommand REFER = new ReferSubmissionCommand(
            "Aggregate limit above authority", "Chief Underwriting Officer",
            "Senior UW - Casualty", "Needs reinsurance sign-off");
    private static final DecideReferralCommand DECIDE = new DecideReferralCommand(
            "APPROVED", "Chief Underwriter", "Approved within authority");

    private static final String REFER_BODY = """
            {
              "reason": "Aggregate limit above authority",
              "authority": "Chief Underwriting Officer",
              "referredTo": "Senior UW - Casualty",
              "notes": "Needs reinsurance sign-off"
            }
            """;
    private static final String DECIDE_BODY = """
            {
              "decision": "APPROVED",
              "decidedBy": "Chief Underwriter",
              "notes": "Approved within authority"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmissionService service;

    @Test
    void opensTheReferralAndReturnsTheUpdatedSubmission() throws Exception {
        when(service.refer(TENANT, SUBMISSION_ID, REFER, "\"0\"", "referral-key"))
                .thenReturn(new Mutation<>(
                        referral("REFERRED", 1, "PENDING", null, null), "\"1\"", false));

        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("If-Match", "\"0\"")
                        .header("Idempotency-Key", "referral-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.status").value("REFERRED"))
                .andExpect(jsonPath("$.referralReason")
                        .value("Aggregate limit above authority"))
                .andExpect(jsonPath("$.referralAuthority")
                        .value("Chief Underwriting Officer"))
                .andExpect(jsonPath("$.referredTo").value("Senior UW - Casualty"))
                .andExpect(jsonPath("$.referralNotes")
                        .value("Needs reinsurance sign-off"))
                .andExpect(jsonPath("$.referralDecision").value("PENDING"))
                .andExpect(jsonPath("$.version").value(1));

        verify(service).refer(TENANT, SUBMISSION_ID, REFER, "\"0\"", "referral-key");
    }

    @Test
    void opensTheReferralWithoutOptionalHeaders() throws Exception {
        when(service.refer(TENANT, SUBMISSION_ID, REFER, null, null))
                .thenReturn(new Mutation<>(
                        referral("REFERRED", 1, "PENDING", null, null), null, false));

        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.referralDecision").value("PENDING"));

        verify(service).refer(TENANT, SUBMISSION_ID, REFER, null, null);
    }

    @Test
    void derivesTheEtagFromTheReturnedVersionWhenTheExpertOmitsIt() throws Exception {
        when(service.refer(TENANT, SUBMISSION_ID, REFER, null, null))
                .thenReturn(new Mutation<>(
                        referral("REFERRED", 3, "PENDING", null, null), null, false));

        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));
    }

    @Test
    void decidesTheReferralAndReturnsTheUpdatedSubmission() throws Exception {
        when(service.decideReferral(TENANT, SUBMISSION_ID, DECIDE, "\"1\"", "decision-key"))
                .thenReturn(new Mutation<>(
                        referral("REFERRED", 2, "APPROVED", "Chief Underwriter", DECIDED_AT),
                        "\"2\"", false));

        mvc.perform(post(DECISION, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "decision-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECIDE_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("REFERRED"))
                .andExpect(jsonPath("$.referralDecision").value("APPROVED"))
                .andExpect(jsonPath("$.referralDecidedBy").value("Chief Underwriter"))
                .andExpect(jsonPath("$.referralDecidedAt").value("2026-10-22T12:00:00Z"))
                .andExpect(jsonPath("$.version").value(2));

        verify(service).decideReferral(TENANT, SUBMISSION_ID, DECIDE, "\"1\"",
                "decision-key");
    }

    @Test
    void returnsTheDeclinedStatusWhenTheDecisionDeclines() throws Exception {
        when(service.decideReferral(TENANT, SUBMISSION_ID, DECIDE, null, null))
                .thenReturn(new Mutation<>(
                        referral("DECLINED", 2, "DECLINED", "Chief Underwriter", DECIDED_AT),
                        "\"2\"", false));

        mvc.perform(post(DECISION, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECIDE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.referralDecision").value("DECLINED"));
    }

    @Test
    void validatesTheReferralBody() throws Exception {
        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("reason", "authority", "referredTo")));

        verifyNoInteractions(service);
    }

    @Test
    void appliesTheReferralFieldCaps() throws Exception {
        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "%s", "authority": "%s",
                                 "referredTo": "%s", "notes": "%s"}
                                """.formatted("R".repeat(65), "A".repeat(65),
                                "P".repeat(129), "N".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("reason", "authority", "referredTo", "notes")));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheDecisionBody() throws Exception {
        mvc.perform(post(DECISION, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("decision", "decidedBy")));

        verifyNoInteractions(service);
    }

    @Test
    void appliesTheDecisionFieldCaps() throws Exception {
        mvc.perform(post(DECISION, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "%s", "decidedBy": "%s", "notes": "%s"}
                                """.formatted("D".repeat(33), "U".repeat(129),
                                "N".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("decision", "decidedBy", "notes")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        mvc.perform(post(DECISION, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECIDE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertNotReferrableConflict() throws Exception {
        when(service.refer(eq(TENANT), eq(SUBMISSION_ID), any(ReferSubmissionCommand.class),
                eq("\"0\""), eq("referral-key")))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_REFERRABLE",
                        "Submission is QUOTED.",
                        List.of(new ApiException.FieldIssue("status",
                                "SUBMISSION_NOT_REFERRABLE",
                                "only RECEIVED or REFERRED submissions can be referred")),
                        Map.of("currentStatus", "QUOTED")));

        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("If-Match", "\"0\"")
                        .header("Idempotency-Key", "referral-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_REFERRABLE"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.meta.currentStatus").value("QUOTED"));
    }

    @Test
    void preservesTheExpertNotPendingConflict() throws Exception {
        when(service.decideReferral(eq(TENANT), eq(SUBMISSION_ID),
                any(DecideReferralCommand.class), eq("\"1\""), eq("decision-key")))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "REFERRAL_NOT_PENDING",
                        "Submission has no pending referral decision.",
                        List.of(new ApiException.FieldIssue("referralDecision",
                                "REFERRAL_NOT_PENDING", "no pending referral")),
                        Map.of("referralDecision", "APPROVED")));

        mvc.perform(post(DECISION, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "decision-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECIDE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERRAL_NOT_PENDING"))
                .andExpect(jsonPath("$.meta.referralDecision").value("APPROVED"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.refer(eq(TENANT), eq(SUBMISSION_ID), any(ReferSubmissionCommand.class),
                eq(null), eq(null)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                        "Underwriting submission not found"));

        mvc.perform(post(REFERRAL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFER_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
    }

    private static SubmissionDto referral(String status, Integer version, String decision,
                                          String decidedBy, Instant decidedAt) {
        return new SubmissionDto(SUBMISSION_ID, "SUB-2026-0000001", TENANT, APPLICANT_ID,
                "CGL_PRODUCTS", LocalDate.of(2026, 1, 1),
                new BigDecimal("1000000.0000"), "USD", List.of("ASBESTOS", "PFAS"),
                "broker intake", status, version, CREATED, UPDATED, null,
                "Aggregate limit above authority", "Chief Underwriting Officer",
                "Senior UW - Casualty", "Needs reinsurance sign-off", decision,
                decidedBy, decidedAt);
    }
}

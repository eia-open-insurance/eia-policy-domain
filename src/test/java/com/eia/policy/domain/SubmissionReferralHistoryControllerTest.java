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
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
class SubmissionReferralHistoryControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID REFERRAL_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SECOND_REFERRAL_ID =
            UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String DIRECT = "DIRECT";
    private static final String HISTORY =
            "/api/v1/policies/submissions/{submissionId}/referrals";
    private static final Instant OPENED_AT = Instant.parse("2026-10-21T09:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-22T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmissionService service;

    @Test
    void listsTheSubmissionReferralHistoryNewestFirst() throws Exception {
        when(service.listReferrals(TENANT, SUBMISSION_ID))
                .thenReturn(List.of(approved(), pending()));

        mvc.perform(get(HISTORY, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].referralId").value(SECOND_REFERRAL_ID.toString()))
                .andExpect(jsonPath("$[0].submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$[0].reason").value("LIMIT"))
                .andExpect(jsonPath("$[0].authority").value("Chief Underwriting Officer"))
                .andExpect(jsonPath("$[0].referredTo").value("Senior UW - Casualty"))
                .andExpect(jsonPath("$[0].notes")
                        .value("Approved up to the treaty capacity"))
                .andExpect(jsonPath("$[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$[0].decidedBy").value("Chief Underwriter"))
                .andExpect(jsonPath("$[0].decidedAt").value("2026-10-22T12:00:00Z"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-22T09:00:00Z"))
                .andExpect(jsonPath("$[1].referralId").value(REFERRAL_ID.toString()))
                .andExpect(jsonPath("$[1].reason").value("PREMIUM"))
                .andExpect(jsonPath("$[1].decision").value("PENDING"))
                .andExpect(jsonPath("$[1].decidedBy").isEmpty())
                .andExpect(jsonPath("$[1].decidedAt").isEmpty());

        verify(service).listReferrals(TENANT, SUBMISSION_ID);
    }

    @Test
    void returnsAnEmptyArrayWhenTheSubmissionHasNoReferralHistory() throws Exception {
        when(service.listReferrals(TENANT, SUBMISSION_ID)).thenReturn(List.of());

        mvc.perform(get(HISTORY, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).listReferrals(TENANT, SUBMISSION_ID);
    }

    @Test
    void acceptsAnAbsentBookHeader() throws Exception {
        when(service.listReferrals(TENANT, SUBMISSION_ID)).thenReturn(List.of(pending()));

        mvc.perform(get(HISTORY, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decision").value("PENDING"));

        verify(service).listReferrals(TENANT, SUBMISSION_ID);
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(HISTORY, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(HISTORY, SUBMISSION_ID)
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.listReferrals(TENANT, SUBMISSION_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                        "Underwriting submission not found",
                        List.of(), Map.of("submissionId", SUBMISSION_ID.toString())));

        mvc.perform(get(HISTORY, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Underwriting submission not found"))
                .andExpect(jsonPath("$.meta.submissionId").value(SUBMISSION_ID.toString()));
    }

    private static ReferralDto approved() {
        return new ReferralDto(SECOND_REFERRAL_ID, SUBMISSION_ID, "LIMIT",
                "Chief Underwriting Officer", "Senior UW - Casualty",
                "Approved up to the treaty capacity", "APPROVED", "Chief Underwriter",
                DECIDED_AT, Instant.parse("2026-10-22T09:00:00Z"));
    }

    private static ReferralDto pending() {
        return new ReferralDto(REFERRAL_ID, SUBMISSION_ID, "PREMIUM",
                "Chief Underwriting Officer", "Senior UW - Property",
                "Premium above authority", "PENDING", null, null, OPENED_AT);
    }
}

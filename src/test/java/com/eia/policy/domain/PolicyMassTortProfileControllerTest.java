package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyMassTortProfileController.class)
class PolicyMassTortProfileControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final String DIRECT = "DIRECT";
    private static final String PROFILE = "/api/v1/policies/{policyId}/mass-tort-profile";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyMassTortProfileService service;

    @Test
    void returnsTheMassTortProfile() throws Exception {
        when(service.get(TENANT, POLICY_ID)).thenReturn(profile());

        mvc.perform(get(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.tortType").value("TALC"))
                .andExpect(jsonPath("$.triggerType").value("CONTINUOUS_TRIGGER"))
                .andExpect(jsonPath("$.allocationMethod").value("ALL_SUMS"))
                .andExpect(jsonPath("$.allocationNotes").value("Allocate to all triggered policy years"))
                .andExpect(jsonPath("$.coverageBlockStart").value("1978-01-01"))
                .andExpect(jsonPath("$.coverageBlockEnd").value("1985-12-31"))
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-20T12:00:00Z"));
    }

    @Test
    void updatesTheMassTortProfile() throws Exception {
        when(service.update(eq(TENANT), eq(POLICY_ID), any(UpdateMassTortProfileCommand.class)))
                .thenReturn(profile());

        mvc.perform(put(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tortType": "TALC",
                                  "triggerType": "CONTINUOUS_TRIGGER",
                                  "allocationMethod": "ALL_SUMS",
                                  "allocationNotes": "Allocate to all triggered policy years",
                                  "coverageBlockStart": "1978-01-01",
                                  "coverageBlockEnd": "1985-12-31",
                                  "status": "COMPLETE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.tortType").value("TALC"))
                .andExpect(jsonPath("$.triggerType").value("CONTINUOUS_TRIGGER"))
                .andExpect(jsonPath("$.allocationMethod").value("ALL_SUMS"))
                .andExpect(jsonPath("$.coverageBlockStart").value("1978-01-01"))
                .andExpect(jsonPath("$.coverageBlockEnd").value("1985-12-31"))
                .andExpect(jsonPath("$.status").value("COMPLETE"));

        verify(service).update(TENANT, POLICY_ID, command());
    }

    @Test
    void acceptsReadOnlyProfileMembersInTheUpdateBody() throws Exception {
        when(service.update(eq(TENANT), eq(POLICY_ID), any(UpdateMassTortProfileCommand.class)))
                .thenReturn(profile());

        mvc.perform(put(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileId": "%s",
                                  "policyId": "%s",
                                  "tortType": "TALC",
                                  "triggerType": "CONTINUOUS_TRIGGER",
                                  "allocationMethod": "ALL_SUMS",
                                  "allocationNotes": "Allocate to all triggered policy years",
                                  "coverageBlockStart": "1978-01-01",
                                  "coverageBlockEnd": "1985-12-31",
                                  "status": "COMPLETE",
                                  "updatedAt": "2026-10-20T12:00:00Z"
                                }
                                """.formatted(PROFILE_ID, POLICY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()));

        verify(service).update(TENANT, POLICY_ID, command());
    }

    @Test
    void acceptsTheTriggerAliasUsedByThePolicyUi() throws Exception {
        when(service.update(eq(TENANT), eq(POLICY_ID), any(UpdateMassTortProfileCommand.class)))
                .thenReturn(profile());

        mvc.perform(put(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tortType": "TALC",
                                  "trigger": "CONTINUOUS_TRIGGER",
                                  "allocationMethod": "ALL_SUMS",
                                  "allocationNotes": "Allocate to all triggered policy years",
                                  "coverageBlockStart": "1978-01-01",
                                  "coverageBlockEnd": "1985-12-31",
                                  "status": "COMPLETE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerType").value("CONTINUOUS_TRIGGER"));

        verify(service).update(TENANT, POLICY_ID, command());
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.get(TENANT, POLICY_ID)).thenReturn(profile());

        mvc.perform(get(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()));
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(service.get(TENANT, POLICY_ID)).thenReturn(profile());

        mvc.perform(get(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(PROFILE, POLICY_ID)
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
        mvc.perform(get(PROFILE, POLICY_ID)
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheUpdateBody() throws Exception {
        mvc.perform(put(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("tortType", "triggerType", "allocationMethod", "status")));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.get(TENANT, POLICY_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "MASS_TORT_PROFILE_NOT_FOUND",
                        "Policy " + POLICY_ID + " has no mass tort profile"));

        mvc.perform(get(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MASS_TORT_PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.update(eq(TENANT), eq(POLICY_ID), any(UpdateMassTortProfileCommand.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MASS_TORT_ALLOCATION",
                        "allocation percentages must sum to 1",
                        List.of(new ApiException.FieldIssue("allocationMethod",
                                "ALLOCATION_SUM_INVALID", "allocations must sum to 1"))));

        mvc.perform(put(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tortType": "TALC", "triggerType": "CONTINUOUS_TRIGGER",
                                 "allocationMethod": "CUSTOM_MATRIX", "status": "DRAFT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MASS_TORT_ALLOCATION"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("allocationMethod"))
                .andExpect(jsonPath("$.errors[0].code").value("ALLOCATION_SUM_INVALID"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.update(eq(TENANT), eq(POLICY_ID), any(UpdateMassTortProfileCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "The mass tort profile changed since it was read",
                        List.of(new ApiException.FieldIssue("updatedAt", "STALE_PROFILE",
                                "reload the profile and retry"))));

        mvc.perform(put(PROFILE, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tortType": "TALC", "triggerType": "CONTINUOUS_TRIGGER",
                                 "allocationMethod": "ALL_SUMS", "status": "DRAFT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].code").value("STALE_PROFILE"));
    }

    private static MassTortProfile profile() {
        return new MassTortProfile(PROFILE_ID, POLICY_ID, "TALC", "CONTINUOUS_TRIGGER",
                "ALL_SUMS", "Allocate to all triggered policy years",
                LocalDate.of(1978, 1, 1), LocalDate.of(1985, 12, 31), "COMPLETE",
                Instant.parse("2026-10-20T12:00:00Z"));
    }

    private static UpdateMassTortProfileCommand command() {
        return new UpdateMassTortProfileCommand("TALC", "CONTINUOUS_TRIGGER", "ALL_SUMS",
                "Allocate to all triggered policy years", LocalDate.of(1978, 1, 1),
                LocalDate.of(1985, 12, 31), "COMPLETE");
    }
}

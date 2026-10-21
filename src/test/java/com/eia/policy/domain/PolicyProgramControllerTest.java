package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyProgramController.class)
@Import(PolicyProgramService.class)
class PolicyProgramControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PROGRAM_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final String DIRECT = "DIRECT";
    private static final String PATH = "/api/v1/policies/{policyId}/program";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyClient client;

    @Test
    void assignsThePolicyProgramAndEchoesTheExpertEtag() throws Exception {
        when(client.updatePolicyProgram(TENANT, POLICY_ID, PROGRAM_ID))
                .thenReturn(new Mutation<>(policy(PROGRAM_ID, 5), "\"5\"", false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programId": "%s"}
                                """.formatted(PROGRAM_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$.programId").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-0000001"));

        verify(client).updatePolicyProgram(TENANT, POLICY_ID, PROGRAM_ID);
    }

    @Test
    void clearsThePolicyProgramWithANullProgramId() throws Exception {
        when(client.updatePolicyProgram(TENANT, POLICY_ID, null))
                .thenReturn(new Mutation<>(policy(null, 6), null, false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programId": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").doesNotExist())
                .andExpect(jsonPath("$.version").value(6));

        verify(client).updatePolicyProgram(TENANT, POLICY_ID, null);
    }

    @Test
    void treatsAnAbsentBodyAsClearingTheProgram() throws Exception {
        when(client.updatePolicyProgram(TENANT, POLICY_ID, null))
                .thenReturn(new Mutation<>(policy(null, 6), null, false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").doesNotExist());

        verify(client).updatePolicyProgram(TENANT, POLICY_ID, null);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(client.updatePolicyProgram(eq(TENANT), eq(POLICY_ID), eq(PROGRAM_ID)))
                .thenReturn(new Mutation<>(policy(PROGRAM_ID, 5), "\"5\"", false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programId": "%s"}
                                """.formatted(PROGRAM_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(PROGRAM_ID.toString()));
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programId": "%s"}
                                """.formatted(PROGRAM_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(client);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programId": "%s"}
                                """.formatted(PROGRAM_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(client.updatePolicyProgram(TENANT, POLICY_ID, PROGRAM_ID))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "The policy changed since it was read",
                        List.of(new ApiException.FieldIssue("expectedVersion", "STALE_VERSION",
                                "reload the policy and retry")),
                        Map.of("currentVersion", 6)));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programId": "%s"}
                                """.formatted(PROGRAM_ID)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"))
                .andExpect(jsonPath("$.errors[0].code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.meta.currentVersion").value(6));
    }

    private static PolicyDto policy(UUID programId, int version) {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        return new PolicyDto(POLICY_ID, TENANT, "POL-2026-0000001", "CGL",
                UUID.fromString("22222222-2222-7222-8222-222222222222"), "ISSUED",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                new java.math.BigDecimal("125000.0000"), "USD",
                new java.math.BigDecimal("2000000.0000"), "USD", version, now, now, null,
                programId, null);
    }
}

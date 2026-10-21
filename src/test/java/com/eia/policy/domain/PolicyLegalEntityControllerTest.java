package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyLegalEntityController.class)
@Import(PolicyLegalEntityService.class)
class PolicyLegalEntityControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID LEGAL_ENTITY_ID =
            UUID.fromString("77777777-7777-7777-8777-777777777777");
    private static final String DIRECT = "DIRECT";
    private static final String PATH = "/api/v1/policies/{policyId}/legal-entity";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyClient client;

    @Test
    void assignsThePolicyLegalEntityAndEchoesTheExpertEtag() throws Exception {
        when(client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID))
                .thenReturn(new Mutation<>(policy(LEGAL_ENTITY_ID, 7), "\"7\"", false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId": "%s"}
                                """.formatted(LEGAL_ENTITY_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.legalEntityId").value(LEGAL_ENTITY_ID.toString()))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-0000001"));

        verify(client).updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID))
                .thenReturn(new Mutation<>(policy(LEGAL_ENTITY_ID, 7), "\"7\"", false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId": "%s"}
                                """.formatted(LEGAL_ENTITY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntityId").value(LEGAL_ENTITY_ID.toString()));
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId": "%s"}
                                """.formatted(LEGAL_ENTITY_ID)))
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
                                {"legalEntityId": "%s"}
                                """.formatted(LEGAL_ENTITY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void clearsThePolicyLegalEntityWhenTheIdIsAbsent() throws Exception {
        when(client.updatePolicyLegalEntity(TENANT, POLICY_ID, null))
                .thenReturn(new Mutation<>(policy(null, 8), "\"8\"", false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"8\""));
        verify(client).updatePolicyLegalEntity(TENANT, POLICY_ID, null);
    }

    @Test
    void clearsThePolicyLegalEntityWhenTheIdIsNull() throws Exception {
        when(client.updatePolicyLegalEntity(TENANT, POLICY_ID, null))
                .thenReturn(new Mutation<>(policy(null, 8), "\"8\"", false));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId": null}
                                """))
                .andExpect(status().isOk());
        verify(client).updatePolicyLegalEntity(TENANT, POLICY_ID, null);
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "LEGAL_ENTITY_NOT_FOUND",
                        "Legal entity " + LEGAL_ENTITY_ID + " does not exist for this tenant"));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId": "%s"}
                                """.formatted(LEGAL_ENTITY_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LEGAL_ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "The policy changed since it was read",
                        List.of(new ApiException.FieldIssue("expectedVersion", "STALE_VERSION",
                                "reload the policy and retry")),
                        Map.of("currentVersion", 8)));

        mvc.perform(patch(PATH, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId": "%s"}
                                """.formatted(LEGAL_ENTITY_ID)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"))
                .andExpect(jsonPath("$.errors[0].code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.meta.currentVersion").value(8));
    }

    private static PolicyDto policy(UUID legalEntityId, int version) {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        return new PolicyDto(POLICY_ID, TENANT, "POL-2026-0000001", "CGL",
                UUID.fromString("22222222-2222-7222-8222-222222222222"), "ISSUED",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                new BigDecimal("125000.0000"), "USD",
                new BigDecimal("2000000.0000"), "USD", version, now, now, null,
                null, legalEntityId);
    }
}

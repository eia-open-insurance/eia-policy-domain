package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import(PolicyService.class)
class PolicyControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PARTY = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);
    private static final LocalDate EXPIRATION = LocalDate.of(2027, 1, 1);
    private static final Instant CREATED = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicyClient client;

    @Test
    void requiresTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/policies"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.path").value("/api/v1/policies"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsMalformedTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/policies").header("X-EIA-Tenant", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TENANT_HEADER"));

        verifyNoInteractions(client);
    }

    @Test
    void listsPolicies() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(policy("DRAFT", 0)));

        mvc.perform(get("/api/v1/policies").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyNumber").value("POL-2026-0000001"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void returnsStructuredNotFound() throws Exception {
        when(client.findById(TENANT, POLICY_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND",
                        "Policy not found: " + POLICY_ID));

        mvc.perform(get("/api/v1/policies/{id}", POLICY_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    void rejectsExpirationBeforeEffectiveDate() throws Exception {
        CreatePolicyCommand command = command(EFFECTIVE.plusDays(2), EFFECTIVE,
                new BigDecimal("1000.00"), new BigDecimal("1000000.00"));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("expirationDate"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsNegativeLimit() throws Exception {
        CreatePolicyCommand command = command(EFFECTIVE, EXPIRATION,
                new BigDecimal("1000.00"), new BigDecimal("-0.01"));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("limitAmount"));

        verifyNoInteractions(client);
    }

    @Test
    void createsPolicyThroughExpert() throws Exception {
        when(client.create(eq(TENANT), any(CreatePolicyCommand.class))).thenReturn(policy("DRAFT", 0));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(EFFECTIVE, EXPIRATION,
                                new BigDecimal("125000.00"), new BigDecimal("2000000.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-0000001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(client).create(eq(TENANT), any(CreatePolicyCommand.class));
    }

    @Test
    void rejectsIllegalStatusTransitionBeforeCallingExpert() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 0));

        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ISSUED","expectedVersion":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        verify(client, never()).updateStatus(any(), any(), any(), anyInt(), any());
    }

    @Test
    void advancesStatusThroughExpert() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 0));
        when(client.updateStatus(TENANT, POLICY_ID, "QUOTED", 0, "quote issued"))
                .thenReturn(policy("QUOTED", 1));

        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"QUOTED","expectedVersion":0,"reason":"quote issued"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.version").value(1));

        verify(client).updateStatus(TENANT, POLICY_ID, "QUOTED", 0, "quote issued");
    }

    private static CreatePolicyCommand command(LocalDate effective, LocalDate expiration,
                                               BigDecimal premium, BigDecimal limit) {
        return new CreatePolicyCommand(null, "CGL", PARTY, effective, expiration,
                premium, "USD", limit, "USD");
    }

    private static PolicyDto policy(String status, int version) {
        return new PolicyDto(POLICY_ID, TENANT, "POL-2026-0000001", "CGL", PARTY, status,
                EFFECTIVE, EXPIRATION, new BigDecimal("125000.0000"), "USD",
                new BigDecimal("2000000.0000"), "USD", version, CREATED, CREATED);
    }
}

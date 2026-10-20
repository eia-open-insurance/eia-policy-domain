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
import java.util.Map;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockitoBean
    private PolicyQueryRepository queryRepository;

    @Test
    void requiresTenantHeaderAsProblem() throws Exception {
        mvc.perform(get("/api/v1/policies"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://errors.eia-open-insurance.example/policy/missing-tenant-header"))
                .andExpect(jsonPath("$.title").value("Tenant context required"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/policies"))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"))
                .andExpect(jsonPath("$.errors[0].code").value("MISSING_TENANT_HEADER"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsMalformedTenantHeaderAsProblem() throws Exception {
        mvc.perform(get("/api/v1/policies").header("X-EIA-Tenant", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TENANT_HEADER"))
                .andExpect(jsonPath("$.detail").value("The X-EIA-Tenant header must be a UUID."))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void listsPoliciesWithDefaultPageEnvelope() throws Exception {
        when(client.findAll(TENANT, 0, 20))
                .thenReturn(new ExpertPolicyPage(List.of(policy("DRAFT", 0)), 0, 20, 1));

        mvc.perform(get("/api/v1/policies").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].policyNumber").value("POL-2026-0000001"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/policies?page=0&size=20"))
                .andExpect(jsonPath("$._links.next").doesNotExist())
                .andExpect(jsonPath("$._links.prev").doesNotExist());
    }

    @Test
    void paginationLinksAtInteriorBoundary() throws Exception {
        when(client.findAll(TENANT, 1, 20))
                .thenReturn(new ExpertPolicyPage(List.of(policy("DRAFT", 0)), 1, 20, 41));

        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/policies?page=1&size=20"))
                .andExpect(jsonPath("$._links.next.href").value("/api/v1/policies?page=2&size=20"))
                .andExpect(jsonPath("$._links.prev.href").value("/api/v1/policies?page=0&size=20"));
    }

    @Test
    void lastPageHasPreviousButNoNextLink() throws Exception {
        when(client.findAll(TENANT, 2, 20))
                .thenReturn(new ExpertPolicyPage(List.of(policy("DRAFT", 0)), 2, 20, 41));

        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("page", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.prev.href").value("/api/v1/policies?page=1&size=20"))
                .andExpect(jsonPath("$._links.next").doesNotExist());
    }

    @Test
    void filtersPoliciesByInsuredPartyId() throws Exception {
        when(queryRepository.findPageByInsuredParty(TENANT, PARTY, 0, 20))
                .thenReturn(new PolicyPageData(List.of(policy("DRAFT", 0)), 1));

        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("insuredPartyId", PARTY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.items[0].insuredPartyId").value(PARTY.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$._links.self.href").value(
                        "/api/v1/policies?insuredPartyId=" + PARTY + "&page=0&size=20"));

        verify(queryRepository).findPageByInsuredParty(TENANT, PARTY, 0, 20);
        verifyNoInteractions(client);
    }

    @Test
    void returnsAnEmptyPageWhenNoPolicyMatchesTheInsuredParty() throws Exception {
        when(queryRepository.findPageByInsuredParty(TENANT, PARTY, 0, 20))
                .thenReturn(new PolicyPageData(List.of(), 0));

        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("insuredPartyId", PARTY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$._links.self.href").value(
                        "/api/v1/policies?insuredPartyId=" + PARTY + "&page=0&size=20"))
                .andExpect(jsonPath("$._links.next").doesNotExist())
                .andExpect(jsonPath("$._links.prev").doesNotExist());
    }

    @Test
    void scopesTheInsuredPartyFilterToTheTenant() throws Exception {
        UUID otherTenant = UUID.fromString("44444444-4444-7444-8444-444444444444");
        when(queryRepository.findPageByInsuredParty(otherTenant, PARTY, 0, 20))
                .thenReturn(new PolicyPageData(List.of(), 0));

        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", otherTenant.toString())
                        .param("insuredPartyId", PARTY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(queryRepository).findPageByInsuredParty(otherTenant, PARTY, 0, 20);
        verify(queryRepository, never()).findPageByInsuredParty(TENANT, PARTY, 0, 20);
    }

    @Test
    void rejectsAMalformedInsuredPartyId() throws Exception {
        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("insuredPartyId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("insuredPartyId"));

        verifyNoInteractions(client, queryRepository);
    }

    @Test
    void pagingKeepsTheInsuredPartyFilterInTheLinks() throws Exception {
        when(queryRepository.findPageByInsuredParty(TENANT, PARTY, 1, 5))
                .thenReturn(new PolicyPageData(
                        List.of(policy("DRAFT", 0), policy("QUOTED", 1)), 12));

        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("insuredPartyId", PARTY.toString())
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$._links.self.href").value(
                        "/api/v1/policies?insuredPartyId=" + PARTY + "&page=1&size=5"))
                .andExpect(jsonPath("$._links.next.href").value(
                        "/api/v1/policies?insuredPartyId=" + PARTY + "&page=2&size=5"))
                .andExpect(jsonPath("$._links.prev.href").value(
                        "/api/v1/policies?insuredPartyId=" + PARTY + "&page=0&size=5"));

        verify(queryRepository).findPageByInsuredParty(TENANT, PARTY, 1, 5);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsSizeAboveMaximum() throws Exception {
        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mvc.perform(get("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        verifyNoInteractions(client);
    }

    @Test
    void returnsStructuredNotFoundProblem() throws Exception {
        when(client.findById(TENANT, POLICY_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND",
                        "Policy not found: " + POLICY_ID));

        mvc.perform(get("/api/v1/policies/{id}", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Policy not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/policies/" + POLICY_ID));
    }

    @Test
    void returnsEtagDerivedFromVersionOnGet() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 3));

        mvc.perform(get("/api/v1/policies/{id}", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void rejectsStaleIfMatchWithCanonicalConflict() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 2));

        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"QUOTED","expectedVersion":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.meta.currentVersion").value(2))
                .andExpect(jsonPath("$.meta.expectedVersion").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("If-Match"))
                .andExpect(jsonPath("$.errors[0].code").value("STALE_VERSION"));

        verify(client, never()).updateStatus(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void ifMatchTakesPrecedenceOverExpectedVersion() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 1));
        when(client.updateStatus(TENANT, POLICY_ID, "QUOTED", 1, "quote issued", "\"1\""))
                .thenReturn(policy("QUOTED", 2));

        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"QUOTED","expectedVersion":0,"reason":"quote issued"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.version").value(2));

        verify(client).updateStatus(TENANT, POLICY_ID, "QUOTED", 1, "quote issued", "\"1\"");
    }

    @Test
    void rejectsMalformedIfMatch() throws Exception {
        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "not-a-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"QUOTED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"))
                .andExpect(jsonPath("$.errors[0].field").value("If-Match"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsStaleExpectedVersionWithoutIfMatch() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 2));

        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"QUOTED","expectedVersion":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
    }

    @Test
    void rejectsIllegalStatusTransitionAsConflict() throws Exception {
        when(client.findById(TENANT, POLICY_ID)).thenReturn(policy("DRAFT", 0));

        mvc.perform(patch("/api/v1/policies/{id}/status", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ISSUED","expectedVersion":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verify(client, never()).updateStatus(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void createsPolicyAndForwardsIdempotencyKey() throws Exception {
        when(client.create(eq(TENANT), any(CreatePolicyCommand.class), eq("idem-1")))
                .thenReturn(new CreateOutcome(policy("DRAFT", 0), false));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(EFFECTIVE, EXPIRATION,
                                new BigDecimal("125000.00"), new BigDecimal("2000000.00")))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-0000001"));

        verify(client).create(eq(TENANT), any(CreatePolicyCommand.class), eq("idem-1"));
    }

    @Test
    void replaysIdempotentCreateWithOkStatus() throws Exception {
        when(client.create(eq(TENANT), any(CreatePolicyCommand.class), eq("idem-1")))
                .thenReturn(new CreateOutcome(policy("DRAFT", 0), true));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(EFFECTIVE, EXPIRATION,
                                new BigDecimal("125000.00"), new BigDecimal("2000000.00")))))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-0000001"));
    }

    @Test
    void returnsConflictForReusedIdempotencyKey() throws Exception {
        when(client.create(eq(TENANT), any(CreatePolicyCommand.class), eq("idem-1")))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key was already used with a different request.",
                        List.of(new ApiException.FieldIssue(
                                "Idempotency-Key", "IDEMPOTENCY_CONFLICT", "different request")),
                        Map.of("existingPolicyId", POLICY_ID.toString())));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(EFFECTIVE, EXPIRATION,
                                new BigDecimal("125000.00"), new BigDecimal("2000000.00")))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.meta.existingPolicyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    void rejectsOverlongIdempotencyKey() throws Exception {
        String key = "k".repeat(256);

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(EFFECTIVE, EXPIRATION,
                                new BigDecimal("125000.00"), new BigDecimal("2000000.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));

        verifyNoInteractions(client);
    }

    @Test
    void mapsExpertUnavailableToDependencyProblem() throws Exception {
        when(client.findAll(TENANT, 0, 20)).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "EXPERT_UNAVAILABLE",
                "The Policy expert is unavailable; retry later."));

        mvc.perform(get("/api/v1/policies").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EXPERT_UNAVAILABLE"))
                .andExpect(jsonPath("$.category").value("DEPENDENCY"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void unexpectedErrorIsInternalProblemWithoutLeakingDetails() throws Exception {
        when(client.findAll(TENANT, 0, 20)).thenThrow(new IllegalStateException("database password leaked"));

        mvc.perform(get("/api/v1/policies").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.category").value("INTERNAL"))
                .andExpect(jsonPath("$.detail").value(
                        "The Policy domain service could not complete the request."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database password"))));
    }

    @Test
    void rejectsExpirationBeforeEffectiveDateWithFieldErrors() throws Exception {
        CreatePolicyCommand command = command(EFFECTIVE.plusDays(2), EFFECTIVE,
                new BigDecimal("1000.00"), new BigDecimal("1000000.00"));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("expirationDate"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.details").doesNotExist());

        verifyNoInteractions(client);
    }

    @Test
    void rejectsNegativeLimitWithFieldErrors() throws Exception {
        CreatePolicyCommand command = command(EFFECTIVE, EXPIRATION,
                new BigDecimal("1000.00"), new BigDecimal("-0.01"));

        mvc.perform(post("/api/v1/policies")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("limitAmount"))
                .andExpect(jsonPath("$.errors[0].code").value("MIN_VALUE"));

        verifyNoInteractions(client);
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

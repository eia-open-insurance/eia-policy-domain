package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyPremiumAuditController.class)
class PolicyPremiumAuditControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID AUDIT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPROVED_BY = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID APPROVAL_REF = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String DIRECT = "DIRECT";
    private static final String AUDIT = "/api/v1/policies/{policyId}/premium-audit";
    private static final ApplyPremiumAuditCommand COMMAND = new ApplyPremiumAuditCommand(
            new BigDecimal("12500.5000"), LocalDate.of(2026, 11, 1),
            "Annual exposure audit", APPROVED_BY, APPROVAL_REF);
    private static final String BODY = """
            {
              "auditedPremium": 12500.5000,
              "effectiveDate": "2026-11-01",
              "reason": "Annual exposure audit",
              "approvedBy": "%s",
              "approvalRef": "%s"
            }
            """.formatted(APPROVED_BY, APPROVAL_REF);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyPremiumAuditService service;

    @Test
    void appliesTheAuditAndReturnsThePolicyAndAuditEnvelope() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1"))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), audit()),
                        "\"5\"", false));

        mvc.perform(auditRequest()
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "audit-key-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.policy.id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.policy.policyNumber").value("GL-2026-0041827"))
                .andExpect(jsonPath("$.policy.premiumAmount").value("12500.5000"))
                .andExpect(jsonPath("$.policy.version").value(5))
                .andExpect(jsonPath("$.premiumAudit.auditId").value(AUDIT_ID.toString()))
                .andExpect(jsonPath("$.premiumAudit.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.premiumAudit.auditedPremium").value("12500.5000"))
                .andExpect(jsonPath("$.premiumAudit.previousPremium").value("12000.0000"))
                .andExpect(jsonPath("$.premiumAudit.premiumDelta").value("500.5000"))
                .andExpect(jsonPath("$.premiumAudit.currency").value("USD"))
                .andExpect(jsonPath("$.premiumAudit.effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.premiumAudit.reason").value("Annual exposure audit"))
                .andExpect(jsonPath("$.premiumAudit.approvedBy").value(APPROVED_BY.toString()))
                .andExpect(jsonPath("$.premiumAudit.approvalRef").value(APPROVAL_REF.toString()))
                .andExpect(jsonPath("$.premiumAudit.status").value("APPLIED"));

        verify(service).apply(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1");
    }

    @Test
    void returnsOkWhenTheExpertReplaysTheAudit() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, null, "audit-key-1"))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), audit()),
                        "\"5\"", true));

        mvc.perform(auditRequest().header("Idempotency-Key", "audit-key-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.policy.id").value(POLICY_ID.toString()));

        verify(service).apply(TENANT, POLICY_ID, COMMAND, null, "audit-key-1");
    }

    @Test
    void returnsTheUpdatedPolicyWhenTheExpertReturnsOnlyThePolicy() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1"))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), null),
                        "\"5\"", false));

        mvc.perform(auditRequest()
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "audit-key-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.policyNumber").value("GL-2026-0041827"))
                .andExpect(jsonPath("$.premiumAmount").value("12500.5000"))
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.policy").doesNotExist())
                .andExpect(jsonPath("$.premiumAudit").doesNotExist());

        verify(service).apply(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1");
    }

    @Test
    void derivesTheETagFromThePolicyWhenTheExpertOmitsIt() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), null), null, false));

        mvc.perform(auditRequest())
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.policy").doesNotExist());
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), audit()),
                        "\"5\"", false));

        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policy.id").value(POLICY_ID.toString()));
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), audit()),
                        "\"5\"", false));

        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(auditRequest().header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheAuditBody() throws Exception {
        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("auditedPremium", "effectiveDate", "reason")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANegativeAuditedPremium() throws Exception {
        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"auditedPremium": -1.0000, "effectiveDate": "2026-11-01",
                                 "reason": "Invalid audit"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("auditedPremium")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAPremiumWithMoreThanFourFractionDigits() throws Exception {
        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"auditedPremium": 1.12345, "effectiveDate": "2026-11-01",
                                 "reason": "Too precise"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("auditedPremium")));

        verifyNoInteractions(service);
    }

    @Test
    void acceptsOptionalApprovalFieldsAbsent() throws Exception {
        ApplyPremiumAuditCommand command = new ApplyPremiumAuditCommand(
                new BigDecimal("9000.0000"), LocalDate.of(2026, 11, 1),
                "No approval needed", null, null);
        when(service.apply(TENANT, POLICY_ID, command, null, null))
                .thenReturn(new Mutation<>(new PremiumAuditResult(policy(), null), "\"5\"", false));

        mvc.perform(post(AUDIT, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"auditedPremium": 9000.0000, "effectiveDate": "2026-11-01",
                                 "reason": "No approval needed"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.premiumAmount").value("12500.5000"))
                .andExpect(jsonPath("$.policy").doesNotExist())
                .andExpect(jsonPath("$.premiumAudit").doesNotExist());

        verify(service).apply(TENANT, POLICY_ID, command, null, null);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "NO_PREMIUM_CHANGE",
                        "The audited premium must differ from the current policy premium.",
                        List.of(new ApiException.FieldIssue("auditedPremium", "NO_PREMIUM_CHANGE",
                                "The audited premium must differ from the current policy premium."))));

        mvc.perform(auditRequest())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NO_PREMIUM_CHANGE"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("auditedPremium"))
                .andExpect(jsonPath("$.errors[0].code").value("NO_PREMIUM_CHANGE"));
    }

    @Test
    void preservesThePolicyNotFoundProblem() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND",
                        "Policy not found: " + POLICY_ID,
                        List.of(new ApiException.FieldIssue("policyId", "POLICY_NOT_FOUND",
                                "Policy not found: " + POLICY_ID))));

        mvc.perform(auditRequest())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].field").value("policyId"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.apply(TENANT, POLICY_ID, COMMAND, "\"3\"", null))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "Expected version 3 but policy is at version 4; reload and retry",
                        List.of(new ApiException.FieldIssue("If-Match", "STALE_VERSION",
                                "Expected version 3 but policy is at version 4")),
                        Map.of("currentVersion", 4, "expectedVersion", 3)));

        mvc.perform(auditRequest().header("If-Match", "\"3\""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("If-Match"))
                .andExpect(jsonPath("$.errors[0].code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.meta.currentVersion").value(4))
                .andExpect(jsonPath("$.meta.expectedVersion").value(3));
    }

    private static MockHttpServletRequestBuilder auditRequest() {
        return post(AUDIT, POLICY_ID)
                .header("X-EIA-Tenant", TENANT.toString())
                .header("X-EIA-Book", DIRECT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    private static PolicyDto policy() {
        return new PolicyDto(POLICY_ID, TENANT, "GL-2026-0041827", "GL",
                UUID.fromString("22222222-2222-7222-8222-222222222222"), "IN_FORCE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                new BigDecimal("12500.5000"), "USD", new BigDecimal("1000000.0000"), "USD",
                5, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-10-22T09:00:00Z"), null, null, null);
    }

    private static PremiumAuditDto audit() {
        return new PremiumAuditDto(AUDIT_ID, POLICY_ID, new BigDecimal("12500.5000"),
                new BigDecimal("12000.0000"), new BigDecimal("500.5000"), "USD",
                LocalDate.of(2026, 11, 1), "Annual exposure audit", APPROVED_BY, APPROVAL_REF,
                "APPLIED", Instant.parse("2026-10-22T09:00:00Z"));
    }
}

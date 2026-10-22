package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PolicyPremiumAuditClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID INSURED_PARTY_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID AUDIT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPROVED_BY = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID APPROVAL_REF = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert";
    private static final String AUDIT = BASE + "/internal/v1/policies/" + POLICY_ID
            + "/premium-audit";
    private static final ApplyPremiumAuditCommand COMMAND = new ApplyPremiumAuditCommand(
            new BigDecimal("12500.5000"), LocalDate.of(2026, 11, 1),
            "Annual exposure audit", APPROVED_BY, APPROVAL_REF);

    @Test
    void postsTheAuditAndMapsThePolicyAndAuditEnvelope() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("If-Match", "\"4\""))
                .andExpect(header("Idempotency-Key", "audit-key-1"))
                .andExpect(content().json("""
                        {
                          "auditedPremium": 12500.5000,
                          "effectiveDate": "2026-11-01",
                          "reason": "Annual exposure audit",
                          "approvedBy": "%s",
                          "approvalRef": "%s"
                        }
                        """.formatted(APPROVED_BY, APPROVAL_REF), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"5\"")
                        .header("Idempotent-Replayed", "false")
                        .body(envelopeJson()));

        Mutation<PremiumAuditResult> result =
                client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1");

        assertThat(result.etag()).isEqualTo("\"5\"");
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().policy()).satisfies(policy -> {
            assertThat(policy.id()).isEqualTo(POLICY_ID);
            assertThat(policy.tenantId()).isEqualTo(TENANT);
            assertThat(policy.policyNumber()).isEqualTo("GL-2026-0041827");
            assertThat(policy.status()).isEqualTo("IN_FORCE");
            assertThat(policy.premiumAmount()).isEqualByComparingTo("12500.5000");
            assertThat(policy.premiumCurrency()).isEqualTo("USD");
            assertThat(policy.version()).isEqualTo(5);
        });
        assertThat(result.body().premiumAudit()).satisfies(audit -> {
            assertThat(audit.auditId()).isEqualTo(AUDIT_ID);
            assertThat(audit.policyId()).isEqualTo(POLICY_ID);
            assertThat(audit.auditedPremium()).isEqualByComparingTo("12500.5000");
            assertThat(audit.previousPremium()).isEqualByComparingTo("12000.0000");
            assertThat(audit.premiumDelta()).isEqualByComparingTo("500.5000");
            assertThat(audit.currency()).isEqualTo("USD");
            assertThat(audit.effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(audit.reason()).isEqualTo("Annual exposure audit");
            assertThat(audit.approvedBy()).isEqualTo(APPROVED_BY);
            assertThat(audit.approvalRef()).isEqualTo(APPROVAL_REF);
            assertThat(audit.status()).isEqualTo("APPLIED");
            assertThat(audit.createdAt()).isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));
        });
        server.verify();
    }

    @Test
    void acceptsABarePolicyResponseAndLeavesTheAuditNull() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        ApplyPremiumAuditCommand command = new ApplyPremiumAuditCommand(
                new BigDecimal("9000.0000"), LocalDate.of(2026, 11, 1),
                "No approval needed", null, null);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("If-Match"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("""
                        {
                          "auditedPremium": 9000.0000,
                          "effectiveDate": "2026-11-01",
                          "reason": "No approval needed"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(policyJson("12500.5000")));

        Mutation<PremiumAuditResult> result =
                client.applyPremiumAudit(TENANT, POLICY_ID, command, null, null);

        assertThat(result.etag()).isNull();
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().policy().id()).isEqualTo(POLICY_ID);
        assertThat(result.body().premiumAudit()).isNull();
        server.verify();
    }

    @Test
    void acceptsTheAuditAliasAndAReplayedResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"6\"")
                        .header("Idempotent-Replayed", "true")
                        .body(envelopeJson("audit")));

        Mutation<PremiumAuditResult> result =
                client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, "audit-key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.etag()).isEqualTo("\"6\"");
        assertThat(result.body().premiumAudit().auditId()).isEqualTo(AUDIT_ID);
        assertThat(result.body().premiumAudit().auditedPremium())
                .isEqualByComparingTo("12500.5000");
        server.verify();
    }

    @Test
    void acceptsTheAlternateEnvelopeAndSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "updatedPolicy": %s,
                                  "result": {
                                    "premium_audit_id": "%s",
                                    "policy_id": "%s",
                                    "audited_premium": "12500.5000",
                                    "previous_premium": "12000.0000",
                                    "premium_delta": "500.5000",
                                    "premium_currency": "USD",
                                    "effective_date": "2026-11-01",
                                    "reason": "Annual exposure audit",
                                    "approved_by": "%s",
                                    "approval_ref": "%s",
                                    "created_at": "2026-10-22T09:00:00Z"
                                  }
                                }
                                """.formatted(policyJson("12500.5000"), AUDIT_ID, POLICY_ID,
                                APPROVED_BY, APPROVAL_REF)));

        Mutation<PremiumAuditResult> result =
                client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null);

        assertThat(result.body().policy().id()).isEqualTo(POLICY_ID);
        assertThat(result.body().premiumAudit()).satisfies(audit -> {
            assertThat(audit.auditId()).isEqualTo(AUDIT_ID);
            assertThat(audit.policyId()).isEqualTo(POLICY_ID);
            assertThat(audit.auditedPremium()).isEqualByComparingTo("12500.5000");
            assertThat(audit.previousPremium()).isEqualByComparingTo("12000.0000");
            assertThat(audit.premiumDelta()).isEqualByComparingTo("500.5000");
            assertThat(audit.currency()).isEqualTo("USD");
            assertThat(audit.effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(audit.approvedBy()).isEqualTo(APPROVED_BY);
            assertThat(audit.approvalRef()).isEqualTo(APPROVAL_REF);
            assertThat(audit.createdAt()).isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));
        });
        server.verify();
    }

    @Test
    void preservesAValidationProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/no-premium-change",
                                  "title": "No premium change",
                                  "status": 400,
                                  "detail": "The audited premium must differ from the current policy premium.",
                                  "code": "NO_PREMIUM_CHANGE",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "auditedPremium", "code": "NO_PREMIUM_CHANGE",
                                     "message": "The audited premium must differ from the current policy premium."}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("NO_PREMIUM_CHANGE");
                    assertThat(api.getMessage()).contains("must differ");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("auditedPremium");
                        assertThat(issue.code()).isEqualTo("NO_PREMIUM_CHANGE");
                    });
                });
        server.verify();
    }

    @Test
    void preservesThePolicyNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/policy-not-found",
                                  "title": "Policy not found",
                                  "status": 404,
                                  "detail": "Policy not found: %s",
                                  "code": "POLICY_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("POLICY_NOT_FOUND");
                    assertThat(api.getMessage()).contains(POLICY_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesTheConcurrentModificationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("If-Match", "\"3\""))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/concurrent-modification",
                                  "title": "Concurrent modification",
                                  "status": 409,
                                  "detail": "Expected version 3 but policy is at version 4; reload and retry",
                                  "code": "CONCURRENT_MODIFICATION",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "If-Match", "code": "STALE_VERSION",
                                     "message": "Expected version 3 but policy is at version 4"}
                                  ],
                                  "meta": {"currentVersion": 4, "expectedVersion": 3}
                                }
                                """));

        assertThatThrownBy(() -> client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, "\"3\"", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("STALE_VERSION"));
                    assertThat(api.meta())
                            .containsEntry("currentVersion", 4)
                            .containsEntry("expectedVersion", 3);
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    @Test
    void rejectsAnAuditOnlyExpertBody() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(AUDIT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(auditJson()));

        assertThatThrownBy(() -> client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    /**
     * The production RestClient builder is supplied by Spring Boot and writes
     * ISO dates; a plain builder would serialize {@link LocalDate} as an array.
     */
    private static RestClient.Builder builder() {
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper()));
                });
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static String envelopeJson() {
        return envelopeJson("premiumAudit");
    }

    private static String envelopeJson(String auditMember) {
        return """
                {
                  "policy": %s,
                  "%s": %s
                }
                """.formatted(policyJson("12500.5000"), auditMember, auditJson());
    }

    private static String policyJson(String premiumAmount) {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyNumber": "GL-2026-0041827",
                  "productCode": "GL",
                  "insuredPartyId": "%s",
                  "status": "IN_FORCE",
                  "effectiveDate": "2026-01-01",
                  "expirationDate": "2027-01-01",
                  "premiumAmount": "%s",
                  "premiumCurrency": "USD",
                  "limitAmount": "1000000.0000",
                  "limitCurrency": "USD",
                  "version": 5,
                  "createdAt": "2026-01-01T00:00:00Z",
                  "updatedAt": "2026-10-22T09:00:00Z",
                  "renewedFromPolicyId": null,
                  "programId": null,
                  "legalEntityId": null,
                  "unknownExpertField": "ignored"
                }
                """.formatted(POLICY_ID, TENANT, INSURED_PARTY_ID, premiumAmount);
    }

    private static String auditJson() {
        return """
                {
                  "id": "%s",
                  "policyId": "%s",
                  "auditedPremium": "12500.5000",
                  "previousPremium": "12000.0000",
                  "premiumDelta": "500.5000",
                  "currency": "USD",
                  "effectiveDate": "2026-11-01",
                  "reason": "Annual exposure audit",
                  "approvedBy": "%s",
                  "approvalRef": "%s",
                  "status": "APPLIED",
                  "createdAt": "2026-10-22T09:00:00Z",
                  "unknownExpertField": "ignored"
                }
                """.formatted(AUDIT_ID, POLICY_ID, APPROVED_BY, APPROVAL_REF);
    }
}

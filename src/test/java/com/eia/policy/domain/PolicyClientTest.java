package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PolicyClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final String BASE = "http://expert";

    @Test
    void translatesExpertNotFoundProblemToPolicyNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BASE + "/internal/v1/policies/" + POLICY_ID
                        + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/policy-not-found",
                                  "title": "Policy not found",
                                  "status": 404,
                                  "detail": "Policy not found: %s",
                                  "instance": "/internal/v1/policies/%s",
                                  "code": "POLICY_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(POLICY_ID, POLICY_ID)));

        assertThatThrownBy(() -> client.findById(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("POLICY_NOT_FOUND");
                    assertThat(api.getMessage()).isEqualTo("Policy not found: " + POLICY_ID);
                });
        server.verify();
    }

    @Test
    void preservesExpertConflictCodeErrorsAndMeta() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BASE + "/internal/v1/policies/" + POLICY_ID
                        + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/concurrent-modification",
                                  "title": "Concurrent modification",
                                  "status": 409,
                                  "detail": "Expected version 1 but policy is at version 2; reload and retry",
                                  "instance": "/internal/v1/policies/%s",
                                  "code": "CONCURRENT_MODIFICATION",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "If-Match", "code": "STALE_VERSION", "message": "stale"}
                                  ],
                                  "meta": {"currentVersion": 2, "expectedVersion": 1}
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.findById(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("If-Match");
                        assertThat(issue.code()).isEqualTo("STALE_VERSION");
                    });
                    assertThat(api.meta())
                            .containsEntry("currentVersion", 2)
                            .containsEntry("expectedVersion", 1);
                });
        server.verify();
    }

    @Test
    void readsTheExpertVersionPageEnvelope() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BASE + "/internal/v1/policies/" + POLICY_ID
                        + "/versions?tenantId=" + TENANT + "&page=0&size=20"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "items": [
                                    {"id": "44444444-4444-7444-8444-444444444444",
                                     "versionNumber": 2, "changeType": "ENDORSEMENT",
                                     "status": "ISSUED", "immutableHash": "hash"}
                                  ],
                                  "page": 0, "size": 20, "total": 2,
                                  "_links": {"self": {"href": "/internal/v1/policies/x/versions"}}
                                }
                                """));

        ExpertItemPage<PolicyVersionDto> page = client.findVersions(TENANT, POLICY_ID, 0, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).singleElement()
                .satisfies(version -> assertThat(version.changeType()).isEqualTo("ENDORSEMENT"));
        server.verify();
    }

    @Test
    void forwardsTheConcurrencyAndIdempotencyHeadersOnCoverageCreate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BASE + "/internal/v1/policies/" + POLICY_ID
                        + "/coverages?tenantId=" + TENANT))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("If-Match", "\"4\""))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GL_PRODUCTS")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"5\"")
                        .header("Idempotent-Replayed", "false")
                        .body("""
                                {"coverage": {"coverageCode": "GL_PRODUCTS"},
                                 "limit": {"aggregateAmount": "5000000.0000"},
                                 "state": {"remainingAmount": "5000000.0000"}}
                                """));

        CreateCoverageCommand command = new CreateCoverageCommand("GL_PRODUCTS",
                "Products liability", "A", "OCCURRENCE", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), new BigDecimal("0.10"),
                new CreateCoverageCommand.LimitCommand(new BigDecimal("2000000"),
                        new BigDecimal("5000000"), null, new BigDecimal("1000000"),
                        new BigDecimal("5000"), new BigDecimal("0.10"), "USD", null));

        Mutation<CoverageViewDto> mutation = client.createCoverage(TENANT, POLICY_ID, command,
                "\"4\"", "key-1");

        assertThat(mutation.etag()).isEqualTo("\"5\"");
        assertThat(mutation.replayed()).isFalse();
        assertThat(mutation.body().coverage().coverageCode()).isEqualTo("GL_PRODUCTS");
        server.verify();
    }

    @Test
    void translatesExpertBusinessRuleFailuresOnSubresources() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BASE + "/internal/v1/policies/" + POLICY_ID
                        + "/versions/current?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/version-hash-mismatch",
                                  "title": "Policy version hash mismatch",
                                  "status": 409,
                                  "detail": "The current policy version hash does not match its snapshot.",
                                  "code": "VERSION_HASH_MISMATCH",
                                  "category": "CONFLICT",
                                  "retryable": false
                                }
                                """));

        assertThatThrownBy(() -> client.findCurrentVersion(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("VERSION_HASH_MISMATCH");
                });
        server.verify();
    }

    @Test
    void mapsExpertServerFailureToExpertError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BASE + "/internal/v1/policies/" + POLICY_ID
                        + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"code": "INTERNAL_ERROR", "detail": "boom", "status": 500}
                                """));

        assertThatThrownBy(() -> client.findById(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_ERROR");
                });
        server.verify();
    }
}

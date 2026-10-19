package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

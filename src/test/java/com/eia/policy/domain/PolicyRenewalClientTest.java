package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
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

class PolicyRenewalClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID SUCCESSOR_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final UUID PARTY_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final String BASE = "http://expert";
    private static final String RENEW = BASE + "/internal/v1/policies/" + POLICY_ID + "/renew";

    @Test
    void renewsAPolicyForwardingTenantDirectBookHeadersAndPayload() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RENEW + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {
                          "effectiveDate": "2027-01-01",
                          "expirationDate": "2028-01-01",
                          "premiumAmount": 130000.0000,
                          "premiumCurrency": "USD",
                          "limitAmount": 2500000.0000,
                          "limitCurrency": "USD"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"0\"")
                        .body(renewedPolicyJson()));

        Mutation<PolicyDto> mutation = client.renewPolicy(TENANT, POLICY_ID, command());

        assertThat(mutation.etag()).isEqualTo("\"0\"");
        assertThat(mutation.replayed()).isFalse();
        assertThat(mutation.body().id()).isEqualTo(SUCCESSOR_ID);
        assertThat(mutation.body().renewedFromPolicyId()).isEqualTo(POLICY_ID);
        assertThat(mutation.body().policyNumber()).isEqualTo("POL-2026-0000002");
        assertThat(mutation.body().effectiveDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(mutation.body().premiumAmount()).isEqualByComparingTo("130000.0000");
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RENEW + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/validation-error",
                                  "title": "Policy renewal validation failed",
                                  "status": 400,
                                  "detail": "Policy renewal validation failed",
                                  "code": "VALIDATION_ERROR",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "effectiveDate", "code": "INVALID_RENEWAL_EFFECTIVE_DATE",
                                     "message": "effectiveDate must be on or after the source expiration date"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.renewPolicy(TENANT, POLICY_ID, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("effectiveDate");
                        assertThat(issue.code()).isEqualTo("INVALID_RENEWAL_EFFECTIVE_DATE");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RENEW + "?tenantId=" + TENANT))
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

        assertThatThrownBy(() -> client.renewPolicy(TENANT, POLICY_ID, command()))
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
    void preservesTheExpertRenewalConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RENEW + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/renewal-not-allowed",
                                  "title": "Policy is not renewable",
                                  "status": 409,
                                  "detail": "Policy POL-2026-0000001 in status CANCELLED cannot be renewed.",
                                  "code": "RENEWAL_NOT_ALLOWED",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "status", "code": "RENEWAL_NOT_ALLOWED",
                                     "message": "Policy status CANCELLED is not renewable."}
                                  ],
                                  "meta": {"sourcePolicyId": "%s", "sourceStatus": "CANCELLED"}
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.renewPolicy(TENANT, POLICY_ID, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("RENEWAL_NOT_ALLOWED");
                    assertThat(api.details()).singleElement()
                            .satisfies(issue -> assertThat(issue.code())
                                    .isEqualTo("RENEWAL_NOT_ALLOWED"));
                    assertThat(api.meta())
                            .containsEntry("sourcePolicyId", POLICY_ID.toString())
                            .containsEntry("sourceStatus", "CANCELLED");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertRenewalResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RENEW + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"0\""));

        assertThatThrownBy(() -> client.renewPolicy(TENANT, POLICY_ID, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    private static RenewPolicyCommand command() {
        return new RenewPolicyCommand(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1),
                new BigDecimal("130000.0000"), "USD", new BigDecimal("2500000.0000"), "USD");
    }

    private static String renewedPolicyJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyNumber": "POL-2026-0000002",
                  "productCode": "CGL",
                  "insuredPartyId": "%s",
                  "status": "DRAFT",
                  "effectiveDate": "2027-01-01",
                  "expirationDate": "2028-01-01",
                  "premiumAmount": "130000.0000",
                  "premiumCurrency": "USD",
                  "limitAmount": "2500000.0000",
                  "limitCurrency": "USD",
                  "version": 0,
                  "createdAt": "2026-10-20T12:00:00Z",
                  "updatedAt": "2026-10-20T12:00:00Z",
                  "renewedFromPolicyId": "%s"
                }
                """.formatted(SUCCESSOR_ID, TENANT, PARTY_ID, POLICY_ID);
    }

    /**
     * The production RestClient builder is supplied by Spring Boot and writes
     * ISO dates; a plain builder would serialize {@link LocalDate} as an array.
     */
    private static RestClient.Builder builder() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
    }
}

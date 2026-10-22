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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PolicyRatingClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID WORKSHEET_ID = UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final UUID CREATED_BY = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String BASE = "http://expert";
    private static final String RATING = BASE + "/internal/v1/policies/" + POLICY_ID + "/rating";

    @Test
    void postsTheRatingAndMapsTheWorksheetAndAppliedPolicy() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        RatePolicyCommand command = new RatePolicyCommand("RATE_TIMES_EXPOSURE",
                new BigDecimal("0.0125"), new BigDecimal("250000.0000"),
                new BigDecimal("1.250000"), true);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("If-Match", "\"4\""))
                .andExpect(header("Idempotency-Key", "rating-key-1"))
                .andExpect(content().json("""
                        {
                          "ratingMethod": "RATE_TIMES_EXPOSURE",
                          "baseRate": 0.0125,
                          "exposureAmount": 250000.0000,
                          "factor": 1.250000,
                          "apply": true
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"5\"")
                        .header("Idempotent-Replayed", "false")
                        .body(envelopeJson(true)));

        Mutation<PolicyRatingResult> result =
                client.ratePolicy(TENANT, POLICY_ID, command, "\"4\"", "rating-key-1");

        assertThat(result.etag()).isEqualTo("\"5\"");
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().worksheet()).satisfies(worksheet -> {
            assertThat(worksheet.id()).isEqualTo(WORKSHEET_ID);
            assertThat(worksheet.tenantId()).isEqualTo(TENANT);
            assertThat(worksheet.policyId()).isEqualTo(POLICY_ID);
            assertThat(worksheet.ratingMethod()).isEqualTo("RATE_TIMES_EXPOSURE");
            assertThat(worksheet.baseRate()).isEqualByComparingTo("0.012500");
            assertThat(worksheet.exposureAmount()).isEqualByComparingTo("250000.0000");
            assertThat(worksheet.factor()).isEqualByComparingTo("1.250000");
            assertThat(worksheet.computedPremium()).isEqualByComparingTo("3906.2500");
            assertThat(worksheet.currency()).isEqualTo("USD");
            assertThat(worksheet.calcHash()).isEqualTo("a".repeat(64));
            assertThat(worksheet.status()).isEqualTo("CALCULATED");
            assertThat(worksheet.createdAt()).isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));
            assertThat(worksheet.createdBy()).isEqualTo(CREATED_BY);
        });
        assertThat(result.body().policy()).satisfies(policy -> {
            assertThat(policy.id()).isEqualTo(POLICY_ID);
            assertThat(policy.policyNumber()).isEqualTo("GL-2026-0041827");
            assertThat(policy.status()).isEqualTo("DRAFT");
            assertThat(policy.premiumAmount()).isEqualByComparingTo("3906.2500");
            assertThat(policy.version()).isEqualTo(5);
        });
        server.verify();
    }

    @Test
    void postsACalculationOnlyRatingWithTheDocumentedDefaults() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        RatePolicyCommand command = new RatePolicyCommand("FLAT",
                new BigDecimal("1250.0000"), null, null, null);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("If-Match"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("""
                        {
                          "ratingMethod": "FLAT",
                          "baseRate": 1250.0000,
                          "exposureAmount": null,
                          "factor": 1,
                          "apply": false
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotent-Replayed", "false")
                        .body(flatEnvelopeJson()));

        Mutation<PolicyRatingResult> result =
                client.ratePolicy(TENANT, POLICY_ID, command, null, null);

        assertThat(result.etag()).isNull();
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().worksheet().id()).isEqualTo(WORKSHEET_ID);
        assertThat(result.body().worksheet().exposureAmount()).isNull();
        assertThat(result.body().worksheet().ratingMethod()).isEqualTo("FLAT");
        assertThat(result.body().worksheet().computedPremium())
                .isEqualByComparingTo("1250.0000");
        assertThat(result.body().policy()).isNull();
        server.verify();
    }

    @Test
    void acceptsAReplayedRating() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"6\"")
                        .header("Idempotent-Replayed", "true")
                        .body(envelopeJson(true)));

        Mutation<PolicyRatingResult> result = client.ratePolicy(TENANT, POLICY_ID,
                calculationOnlyCommand(), null, "rating-key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.etag()).isEqualTo("\"6\"");
        assertThat(result.body().worksheet().id()).isEqualTo(WORKSHEET_ID);
        server.verify();
    }

    @Test
    void readsTheLatestWorksheetForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(worksheetJson()));

        RatingWorksheetDto worksheet = client.findLatestRating(TENANT, POLICY_ID);

        assertThat(worksheet.id()).isEqualTo(WORKSHEET_ID);
        assertThat(worksheet.tenantId()).isEqualTo(TENANT);
        assertThat(worksheet.policyId()).isEqualTo(POLICY_ID);
        assertThat(worksheet.ratingMethod()).isEqualTo("RATE_TIMES_EXPOSURE");
        assertThat(worksheet.baseRate()).isEqualByComparingTo("0.012500");
        assertThat(worksheet.exposureAmount()).isEqualByComparingTo("250000.0000");
        assertThat(worksheet.factor()).isEqualByComparingTo("1.250000");
        assertThat(worksheet.computedPremium()).isEqualByComparingTo("3906.2500");
        assertThat(worksheet.currency()).isEqualTo("USD");
        assertThat(worksheet.calcHash()).isEqualTo("a".repeat(64));
        assertThat(worksheet.status()).isEqualTo("CALCULATED");
        assertThat(worksheet.createdAt()).isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));
        assertThat(worksheet.createdBy()).isEqualTo(CREATED_BY);
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/rating-exposure-invalid",
                                  "title": "Invalid rating exposure",
                                  "status": 400,
                                  "detail": "exposureAmount must be greater than zero for RATE_TIMES_EXPOSURE.",
                                  "code": "RATING_EXPOSURE_INVALID",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "exposureAmount", "code": "RATING_EXPOSURE_INVALID",
                                     "message": "exposureAmount must be greater than zero for RATE_TIMES_EXPOSURE."}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.ratePolicy(TENANT, POLICY_ID,
                calculationOnlyCommand(), null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("RATING_EXPOSURE_INVALID");
                    assertThat(api.getMessage()).contains("greater than zero");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("exposureAmount");
                        assertThat(issue.code()).isEqualTo("RATING_EXPOSURE_INVALID");
                    });
                });
        server.verify();
    }

    @Test
    void preservesThePolicyNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
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

        assertThatThrownBy(() -> client.ratePolicy(TENANT, POLICY_ID,
                calculationOnlyCommand(), null, null))
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
    void preservesTheNotRateableConflict() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("If-Match", "\"2\""))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/policy-not-rateable",
                                  "title": "Policy is not rateable",
                                  "status": 409,
                                  "detail": "Policy GL-2026-0041827 in status IN_FORCE cannot be rated; only DRAFT or QUOTED policies are rateable.",
                                  "code": "POLICY_NOT_RATEABLE",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "status", "code": "POLICY_NOT_RATEABLE",
                                     "message": "only DRAFT or QUOTED policies are rateable"}
                                  ],
                                  "meta": {
                                    "policyId": "%s",
                                    "sourceStatus": "IN_FORCE",
                                    "allowedStatuses": ["DRAFT", "QUOTED"]
                                  }
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.ratePolicy(TENANT, POLICY_ID,
                calculationOnlyCommand(), "\"2\"", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("POLICY_NOT_RATEABLE");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("status"));
                    assertThat(api.meta())
                            .containsEntry("sourceStatus", "IN_FORCE");
                });
        server.verify();
    }

    @Test
    void preservesTheWorksheetNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/rating-worksheet-not-found",
                                  "title": "Rating worksheet not found",
                                  "status": 404,
                                  "detail": "No rating worksheet exists for policy: %s",
                                  "code": "RATING_WORKSHEET_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.findLatestRating(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("RATING_WORKSHEET_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyRatingResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ratePolicy(TENANT, POLICY_ID,
                calculationOnlyCommand(), null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyLatestWorksheetResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(RATING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findLatestRating(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    private static RatePolicyCommand calculationOnlyCommand() {
        return new RatePolicyCommand("RATE_TIMES_EXPOSURE", new BigDecimal("0.0125"),
                new BigDecimal("250000.0000"), new BigDecimal("1.250000"), false);
    }

    private static RestClient.Builder builder() {
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0,
                            new MappingJackson2HttpMessageConverter(objectMapper()));
                });
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static String envelopeJson(boolean withPolicy) {
        String policy = withPolicy ? ",\n  \"policy\": " + policyJson() : "";
        return "{\n  \"worksheet\": " + worksheetJson() + policy + "\n}";
    }

    private static String flatEnvelopeJson() {
        return """
                {
                  "worksheet": {
                    "id": "%s",
                    "tenantId": "%s",
                    "policyId": "%s",
                    "ratingMethod": "FLAT",
                    "baseRate": "1250.0000",
                    "exposureAmount": null,
                    "factor": "1.000000",
                    "computedPremium": "1250.0000",
                    "currency": "USD",
                    "calcHash": "%s",
                    "status": "CALCULATED",
                    "createdAt": "2026-10-22T09:00:00Z",
                    "createdBy": "%s"
                  }
                }
                """.formatted(WORKSHEET_ID, TENANT, POLICY_ID, "b".repeat(64), CREATED_BY);
    }

    private static String worksheetJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyId": "%s",
                  "ratingMethod": "RATE_TIMES_EXPOSURE",
                  "baseRate": "0.012500",
                  "exposureAmount": "250000.0000",
                  "factor": "1.250000",
                  "computedPremium": "3906.2500",
                  "currency": "USD",
                  "calcHash": "%s",
                  "status": "CALCULATED",
                  "createdAt": "2026-10-22T09:00:00Z",
                  "createdBy": "%s",
                  "unknownExpertField": "ignored"
                }
                """.formatted(WORKSHEET_ID, TENANT, POLICY_ID, "a".repeat(64), CREATED_BY);
    }

    private static String policyJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyNumber": "GL-2026-0041827",
                  "productCode": "GL",
                  "insuredPartyId": "22222222-2222-7222-8222-222222222222",
                  "status": "DRAFT",
                  "effectiveDate": "2026-01-01",
                  "expirationDate": "2027-01-01",
                  "premiumAmount": "3906.2500",
                  "premiumCurrency": "USD",
                  "limitAmount": "1000000.0000",
                  "limitCurrency": "USD",
                  "version": 5,
                  "createdAt": "2026-01-01T00:00:00Z",
                  "updatedAt": "2026-10-22T09:00:00Z",
                  "renewedFromPolicyId": null,
                  "programId": null,
                  "legalEntityId": null
                }
                """.formatted(POLICY_ID, TENANT);
    }
}

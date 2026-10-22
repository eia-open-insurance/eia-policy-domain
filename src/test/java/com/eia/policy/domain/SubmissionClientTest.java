package com.eia.policy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SubmissionClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID PRODUCT_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PROGRAM_ID =
            UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final UUID OWNER_ID =
            UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert";
    private static final String SUBMISSIONS = BASE + "/internal/v1/policies/submissions";
    private static final String SUBMISSION = SUBMISSIONS + "/" + SUBMISSION_ID;
    private static final String STATUS = SUBMISSION + "/status";

    private static final JsonNode REQUESTED_LIMITS = JsonNodeFactory.instance.objectNode()
            .put("perOccurrence", "1000000.0000")
            .put("aggregate", "2000000.0000");
    private static final JsonNode EXPOSURES = JsonNodeFactory.instance.arrayNode()
            .add(JsonNodeFactory.instance.objectNode()
                    .put("classCode", "CGL")
                    .put("amount", "5000000.0000"));

    @Test
    void listsSubmissionsFromABareArrayForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[%s]".formatted(submissionJson())));

        List<SubmissionDto> submissions = client.findSubmissions(TENANT);

        assertThat(submissions).singleElement().satisfies(submission -> {
            assertThat(submission.submissionId()).isEqualTo(SUBMISSION_ID);
            assertThat(submission.submissionNumber()).isEqualTo("SUB-2026-0000001");
            assertThat(submission.applicantPartyId()).isEqualTo(APPLICANT_ID);
            assertThat(submission.productId()).isEqualTo(PRODUCT_ID);
            assertThat(submission.programId()).isEqualTo(PROGRAM_ID);
            assertThat(submission.requestedEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(submission.requestedLimits().get("perOccurrence").asText())
                    .isEqualTo("1000000.0000");
            assertThat(submission.requestedLimits().get("aggregate").asText())
                    .isEqualTo("2000000.0000");
            assertThat(submission.requestedCoverages()).containsExactly("GL", "UMBRELLA");
            assertThat(submission.tortCodes()).containsExactly("ASBESTOS");
            assertThat(submission.exposures().get(0).get("classCode").asText())
                    .isEqualTo("CGL");
            assertThat(submission.status()).isEqualTo("RECEIVED");
            assertThat(submission.owner()).isEqualTo(OWNER_ID);
            assertThat(submission.receivedAt())
                    .isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));
            assertThat(submission.updatedAt())
                    .isEqualTo(Instant.parse("2026-10-22T10:00:00Z"));
            assertThat(submission.version()).isEqualTo(2);
        });
        server.verify();
    }

    @Test
    void acceptsAPagedExpertList() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "items": [%s],
                                  "page": 0,
                                  "size": 20,
                                  "total": 1
                                }
                                """.formatted(submissionJson())));

        List<SubmissionDto> submissions = client.findSubmissions(TENANT);

        assertThat(submissions).singleElement().satisfies(submission ->
                assertThat(submission.submissionId()).isEqualTo(SUBMISSION_ID));
        server.verify();
    }

    @Test
    void readsOneSubmissionAndMapsTheSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson()));

        SubmissionDto submission = client.findSubmission(TENANT, SUBMISSION_ID);

        assertThat(submission.submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(submission.status()).isEqualTo("RECEIVED");
        assertThat(submission.requestedCoverages()).containsExactly("GL", "UMBRELLA");
        assertThat(submission.tortCodes()).containsExactly("ASBESTOS");
        server.verify();
    }

    @Test
    void createsTheSubmissionWithTenantDirectAndIdempotencyHeaders() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        CreateSubmissionCommand command = command();

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("Idempotency-Key", "sub-key"))
                .andExpect(content().json("""
                        {
                          "submissionNumber": null,
                          "applicantPartyId": "%s",
                          "productId": "%s",
                          "programId": "%s",
                          "requestedEffectiveDate": "2026-01-01",
                          "requestedLimits": {"perOccurrence": "1000000.0000",
                                              "aggregate": "2000000.0000"},
                          "requestedCoverages": ["GL", "UMBRELLA"],
                          "tortCodes": ["ASBESTOS"],
                          "exposures": [{"classCode": "CGL", "amount": "5000000.0000"}],
                          "owner": "%s"
                        }
                        """.formatted(APPLICANT_ID, PRODUCT_ID, PROGRAM_ID, OWNER_ID),
                        JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"2\"")
                        .header("Idempotent-Replayed", "false")
                        .body(submissionJson()));

        Mutation<SubmissionDto> result =
                client.createSubmission(TENANT, command, "sub-key");

        assertThat(result.etag()).isEqualTo("\"2\"");
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(result.body().submissionNumber()).isEqualTo("SUB-2026-0000001");
        assertThat(result.body().status()).isEqualTo("RECEIVED");
        server.verify();
    }

    @Test
    void createsWithoutAnIdempotencyKeyWhenTheCallerSuppliesNone() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson()));

        Mutation<SubmissionDto> result =
                client.createSubmission(TENANT, command(), null);

        assertThat(result.etag()).isNull();
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().submissionId()).isEqualTo(SUBMISSION_ID);
        server.verify();
    }

    @Test
    void acceptsAReplayedCreate() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"2\"")
                        .header("Idempotent-Replayed", "true")
                        .body(submissionJson()));

        Mutation<SubmissionDto> result =
                client.createSubmission(TENANT, command(), "sub-key");

        assertThat(result.replayed()).isTrue();
        assertThat(result.etag()).isEqualTo("\"2\"");
        assertThat(result.body().submissionId()).isEqualTo(SUBMISSION_ID);
        server.verify();
    }

    @Test
    void updatesTheStatusForwardingIfMatchAndIdempotency() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("If-Match", "\"2\""))
                .andExpect(header("Idempotency-Key", "status-key"))
                .andExpect(content().json("""
                        {"status": "CLEARANCE", "reason": "sanctions cleared"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"3\"")
                        .header("Idempotent-Replayed", "false")
                        .body(submissionJson()));

        Mutation<SubmissionDto> result = client.updateSubmissionStatus(TENANT, SUBMISSION_ID,
                new UpdateSubmissionStatusCommand("CLEARANCE", "sanctions cleared"),
                "\"2\"", "status-key");

        assertThat(result.etag()).isEqualTo("\"3\"");
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().status()).isEqualTo("RECEIVED");
        server.verify();
    }

    @Test
    void omitsTheReasonWhenTheStatusCommandCarriesNone() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(headerDoesNotExist("If-Match"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("""
                        {"status": "RATING"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson()));

        client.updateSubmissionStatus(TENANT, SUBMISSION_ID,
                new UpdateSubmissionStatusCommand("RATING", null), null, null);

        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-submission",
                                  "title": "Invalid submission",
                                  "status": 400,
                                  "detail": "applicantPartyId must identify a party in the tenant",
                                  "code": "INVALID_SUBMISSION",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "applicantPartyId", "code": "INVALID_PARTY",
                                     "message": "applicantPartyId must identify a party in the tenant"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createSubmission(TENANT, command(), "sub-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_SUBMISSION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("applicantPartyId"));
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/submission-not-found",
                                  "title": "Submission not found",
                                  "status": 404,
                                  "detail": "Submission not found",
                                  "code": "SUBMISSION_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """));

        assertThatThrownBy(() -> client.findSubmission(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("SUBMISSION_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-submission-transition",
                                  "title": "Invalid submission transition",
                                  "status": 409,
                                  "detail": "A submission in RECEIVED cannot move to QUOTED",
                                  "code": "INVALID_SUBMISSION_TRANSITION",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "status", "code": "INVALID_SUBMISSION_TRANSITION",
                                     "message": "run clearance and rating before quoting"}
                                  ],
                                  "meta": {"sourceStatus": "RECEIVED", "targetStatus": "QUOTED"}
                                }
                                """));

        assertThatThrownBy(() -> client.updateSubmissionStatus(TENANT, SUBMISSION_ID,
                new UpdateSubmissionStatusCommand("QUOTED", null), null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("INVALID_SUBMISSION_TRANSITION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("INVALID_SUBMISSION_TRANSITION"));
                    assertThat(api.meta()).containsEntry("sourceStatus", "RECEIVED");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertCreateResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createSubmission(TENANT, command(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertListResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> client.findSubmissions(TENANT))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    @Test
    void rejectsAMalformedExpertListShape() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"total": 1}
                                """));

        assertThatThrownBy(() -> client.findSubmissions(TENANT))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    @Test
    void rejectsASubmissionWithoutAnIdentity() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status": "RECEIVED"}
                                """));

        assertThatThrownBy(() -> client.findSubmission(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    private static CreateSubmissionCommand command() {
        return new CreateSubmissionCommand(null, APPLICANT_ID, PRODUCT_ID, PROGRAM_ID,
                LocalDate.of(2026, 1, 1), REQUESTED_LIMITS, List.of("GL", "UMBRELLA"),
                List.of("ASBESTOS"), EXPOSURES, OWNER_ID);
    }

    private static String submissionJson() {
        return """
                {
                  "id": "%s",
                  "submission_number": "SUB-2026-0000001",
                  "applicant_party_id": "%s",
                  "product_id": "%s",
                  "program_id": "%s",
                  "requested_effective_date": "2026-01-01",
                  "requested_limits": {"perOccurrence": "1000000.0000",
                                       "aggregate": "2000000.0000"},
                  "requested_coverages": ["GL", "UMBRELLA"],
                  "tort_codes": ["ASBESTOS"],
                  "exposures": [{"classCode": "CGL", "amount": "5000000.0000"}],
                  "status": "RECEIVED",
                  "owner": "%s",
                  "received_at": "2026-10-22T09:00:00Z",
                  "updated_at": "2026-10-22T10:00:00Z",
                  "version": 2
                }
                """.formatted(SUBMISSION_ID, APPLICANT_ID, PRODUCT_ID, PROGRAM_ID, OWNER_ID);
    }

    /**
     * The client's own mapper reads the expert list documents, so it needs the
     * same ISO date handling as the production Spring Boot mapper.
     */
    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static RestClient.Builder builder() {
        ObjectMapper mapper = objectMapper();
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
    }
}

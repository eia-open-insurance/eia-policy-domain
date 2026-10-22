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
    private static final UUID OTHER_TENANT =
            UUID.fromString("99999999-9999-7999-8999-999999999999");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final String BASE = "http://expert";
    private static final String SUBMISSIONS = BASE + "/internal/v1/policies/submissions";
    private static final String SUBMISSION = SUBMISSIONS + "/" + SUBMISSION_ID;
    private static final String STATUS = SUBMISSION + "/status";

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

        assertThat(submissions).singleElement().satisfies(SubmissionClientTest::assertCanonical);
        server.verify();
    }

    @Test
    void acceptsAPagedExpertListAndFillsTheTenantScope() {
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
                                """.formatted(submissionJsonWithoutTenant())));

        List<SubmissionDto> submissions = client.findSubmissions(TENANT);

        assertThat(submissions).singleElement().satisfies(submission -> {
            assertThat(submission.submissionId()).isEqualTo(SUBMISSION_ID);
            assertThat(submission.tenantId()).isEqualTo(TENANT);
        });
        server.verify();
    }

    @Test
    void readsOneSubmissionFromABareCanonicalRow() {
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

        assertCanonical(submission);
        server.verify();
    }

    @Test
    void readsOneSubmissionFromASubmissionEnvelope() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(envelope(submissionJson())));

        SubmissionDto submission = client.findSubmission(TENANT, SUBMISSION_ID);

        assertCanonical(submission);
        server.verify();
    }

    @Test
    void prefersTheExpertTenantIdWhenTheRowCarriesOne() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        String body = submissionJson().replace(
                "\"tenantId\": \"" + TENANT + "\"",
                "\"tenantId\": \"" + OTHER_TENANT + "\"");

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        assertThat(client.findSubmission(TENANT, SUBMISSION_ID).tenantId())
                .isEqualTo(OTHER_TENANT);
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
                          "applicantPartyId": "%s",
                          "productCode": "CGL_PRODUCTS",
                          "requestedEffectiveDate": "2026-01-01",
                          "requestedLimitAmount": 1000000.0000,
                          "requestedLimitCurrency": "USD",
                          "tortCodes": ["ASBESTOS", "PFAS"],
                          "notes": "broker intake"
                        }
                        """.formatted(APPLICANT_ID), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"0\"")
                        .header("Idempotent-Replayed", "false")
                        .body(submissionJson()));

        Mutation<SubmissionDto> result =
                client.createSubmission(TENANT, command, "sub-key");

        assertThat(result.etag()).isEqualTo("\"0\"");
        assertThat(result.replayed()).isFalse();
        assertCanonical(result.body());
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
    void acceptsAReplayedCreateFromASubmissionEnvelope() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSIONS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"2\"")
                        .header("Idempotent-Replayed", "true")
                        .body(envelope(submissionJson())));

        Mutation<SubmissionDto> result =
                client.createSubmission(TENANT, command(), "sub-key");

        assertThat(result.replayed()).isTrue();
        assertThat(result.etag()).isEqualTo("\"2\"");
        assertCanonical(result.body());
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
                        {"status": "QUOTED", "reason": "ready to quote"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"3\"")
                        .header("Idempotent-Replayed", "false")
                        .body(envelope(submissionJson("QUOTED", 3))));

        Mutation<SubmissionDto> result = client.updateSubmissionStatus(TENANT, SUBMISSION_ID,
                new UpdateSubmissionStatusCommand("QUOTED", "ready to quote"),
                "\"2\"", "status-key");

        assertThat(result.etag()).isEqualTo("\"3\"");
        assertThat(result.replayed()).isFalse();
        assertThat(result.body().status()).isEqualTo("QUOTED");
        assertThat(result.body().version()).isEqualTo(3);
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
                        {"status": "REFERRED"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson("REFERRED", 3)));

        SubmissionDto submission = client.updateSubmissionStatus(TENANT, SUBMISSION_ID,
                new UpdateSubmissionStatusCommand("REFERRED", null), null, null).body();

        assertThat(submission.status()).isEqualTo("REFERRED");
        server.verify();
    }

    @Test
    void mapsTheSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "submission_number": "SUB-2026-0000001",
                                  "tenant_id": "%s",
                                  "applicant_party_id": "%s",
                                  "product_code": "CGL_PRODUCTS",
                                  "requested_effective_date": "2026-01-01",
                                  "requested_limit_amount": 1000000.0000,
                                  "requested_limit_currency": "USD",
                                  "tort_codes": ["ASBESTOS", "PFAS"],
                                  "notes": "broker intake",
                                  "status": "RECEIVED",
                                  "version": 2,
                                  "created_at": "2026-10-22T09:00:00Z",
                                  "updated_at": "2026-10-22T10:00:00Z"
                                }
                                """.formatted(SUBMISSION_ID, TENANT, APPLICANT_ID)));

        SubmissionDto submission = client.findSubmission(TENANT, SUBMISSION_ID);

        assertCanonical(submission);
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

    @Test
    void rejectsAMalformedSubmissionEnvelope() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"submission": null}
                                """));

        assertThatThrownBy(() -> client.findSubmission(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    private static CreateSubmissionCommand command() {
        return new CreateSubmissionCommand(APPLICANT_ID, "CGL_PRODUCTS",
                LocalDate.of(2026, 1, 1), new BigDecimal("1000000.0000"), "USD",
                List.of("ASBESTOS", "PFAS"), "broker intake");
    }

    private static void assertCanonical(SubmissionDto submission) {
        assertThat(submission.submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(submission.submissionNumber()).isEqualTo("SUB-2026-0000001");
        assertThat(submission.tenantId()).isEqualTo(TENANT);
        assertThat(submission.applicantPartyId()).isEqualTo(APPLICANT_ID);
        assertThat(submission.productCode()).isEqualTo("CGL_PRODUCTS");
        assertThat(submission.requestedEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(submission.requestedLimitAmount()).isEqualByComparingTo("1000000.0000");
        assertThat(submission.requestedLimitCurrency()).isEqualTo("USD");
        assertThat(submission.tortCodes()).containsExactly("ASBESTOS", "PFAS");
        assertThat(submission.notes()).isEqualTo("broker intake");
        assertThat(submission.status()).isEqualTo("RECEIVED");
        assertThat(submission.version()).isEqualTo(2);
        assertThat(submission.createdAt()).isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));
        assertThat(submission.updatedAt()).isEqualTo(Instant.parse("2026-10-22T10:00:00Z"));
    }

    private static String envelope(String submission) {
        return "{\"submission\": " + submission + "}";
    }

    private static String submissionJson() {
        return submissionJson("RECEIVED", 2);
    }

    private static String submissionJsonWithoutTenant() {
        return submissionJson().replace("\"tenantId\": \"" + TENANT + "\",\n", "");
    }

    private static String submissionJson(String status, int version) {
        return """
                {
                  "submissionId": "%s",
                  "submissionNumber": "SUB-2026-0000001",
                  "tenantId": "%s",
                  "applicantPartyId": "%s",
                  "productCode": "CGL_PRODUCTS",
                  "requestedEffectiveDate": "2026-01-01",
                  "requestedLimitAmount": 1000000.0000,
                  "requestedLimitCurrency": "USD",
                  "tortCodes": ["ASBESTOS", "PFAS"],
                  "notes": "broker intake",
                  "status": "%s",
                  "version": %d,
                  "createdAt": "2026-10-22T09:00:00Z",
                  "updatedAt": "2026-10-22T10:00:00Z"
                }
                """.formatted(SUBMISSION_ID, TENANT, APPLICANT_ID, status, version);
    }

    /**
     * The client's own mapper reads the expert documents, so it needs the same
     * ISO date handling as the production Spring Boot mapper.
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

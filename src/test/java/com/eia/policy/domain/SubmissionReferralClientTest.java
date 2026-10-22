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

class SubmissionReferralClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final String BASE = "http://expert";
    private static final String SUBMISSIONS = BASE + "/internal/v1/policies/submissions";
    private static final String SUBMISSION = SUBMISSIONS + "/" + SUBMISSION_ID;
    private static final String REFERRAL = SUBMISSION + "/referral";
    private static final String DECISION = REFERRAL + "/decision";
    private static final ReferSubmissionCommand REFER = new ReferSubmissionCommand(
            "Aggregate limit above authority", "Chief Underwriting Officer",
            "Senior UW - Casualty", "Needs reinsurance sign-off");
    private static final DecideReferralCommand DECIDE = new DecideReferralCommand(
            "APPROVED", "Chief Underwriter", "Approved within authority");

    @Test
    void opensTheReferralForwardingTenantDirectConcurrencyAndIdempotency() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(REFERRAL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("If-Match", "\"0\""))
                .andExpect(header("Idempotency-Key", "referral-key"))
                .andExpect(content().json("""
                        {
                          "reason": "Aggregate limit above authority",
                          "authority": "Chief Underwriting Officer",
                          "referredTo": "Senior UW - Casualty",
                          "notes": "Needs reinsurance sign-off"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"1\"")
                        .body(submissionJson("REFERRED", 1, "PENDING", null, null)));

        Mutation<SubmissionDto> result =
                client.referSubmission(TENANT, SUBMISSION_ID, REFER, "\"0\"", "referral-key");

        assertThat(result.etag()).isEqualTo("\"1\"");
        assertThat(result.replayed()).isFalse();
        assertReferral(result.body(), "PENDING", null, null, 1);
        server.verify();
    }

    @Test
    void opensTheReferralWithoutOptionalHeadersOrNotes() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        ReferSubmissionCommand command = new ReferSubmissionCommand(
                "Limit", "Authority", "Assignee", null);

        server.expect(requestTo(REFERRAL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("If-Match"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("""
                        {"reason": "Limit", "authority": "Authority", "referredTo": "Assignee"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson("REFERRED", 1, "PENDING", null, null)));

        Mutation<SubmissionDto> result = client.referSubmission(TENANT, SUBMISSION_ID, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.etag()).isNull();
        assertReferral(result.body(), "PENDING", null, null, 1);
        server.verify();
    }

    @Test
    void decidesTheReferralForwardingTenantDirectConcurrencyAndIdempotency() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        Instant decidedAt = Instant.parse("2026-10-22T12:00:00Z");

        server.expect(requestTo(DECISION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("If-Match", "\"1\""))
                .andExpect(header("Idempotency-Key", "decision-key"))
                .andExpect(content().json("""
                        {
                          "decision": "APPROVED",
                          "decidedBy": "Chief Underwriter",
                          "notes": "Approved within authority"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"2\"")
                        .body(submissionJson("REFERRED", 2, "APPROVED",
                                "Chief Underwriter", decidedAt)));

        Mutation<SubmissionDto> result = client.decideSubmissionReferral(
                TENANT, SUBMISSION_ID, DECIDE, "\"1\"", "decision-key");

        assertThat(result.etag()).isEqualTo("\"2\"");
        assertThat(result.replayed()).isFalse();
        assertReferral(result.body(), "APPROVED", "Chief Underwriter", decidedAt, 2);
        server.verify();
    }

    @Test
    void decidesWithoutNotes() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);
        DecideReferralCommand command =
                new DecideReferralCommand("DECLINED", "Chief Underwriter", null);

        server.expect(requestTo(DECISION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"decision": "DECLINED", "decidedBy": "Chief Underwriter"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson("DECLINED", 2, "DECLINED", "Chief Underwriter",
                                Instant.parse("2026-10-22T12:00:00Z"))));

        SubmissionDto decided = client.decideSubmissionReferral(TENANT, SUBMISSION_ID, command)
                .body();

        assertThat(decided.status()).isEqualTo("DECLINED");
        assertThat(decided.referralDecision()).isEqualTo("DECLINED");
        server.verify();
    }

    @Test
    void acceptsASubmissionEnvelopeAndMapsTheSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(REFERRAL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "submission": {
                                    "submissionId": "%s",
                                    "submissionNumber": "SUB-2026-0000001",
                                    "status": "REFERRED",
                                    "referral_reason": "Limit",
                                    "required_authority": "Chief Underwriting Officer",
                                    "assigned_to": "Senior UW - Casualty",
                                    "comments": "Needs sign-off",
                                    "referral_decision": "PENDING",
                                    "version": 1,
                                    "created_at": "2026-10-22T09:00:00Z",
                                    "updated_at": "2026-10-22T10:00:00Z"
                                  }
                                }
                                """.formatted(SUBMISSION_ID)));

        SubmissionDto referral = client.referSubmission(TENANT, SUBMISSION_ID,
                new ReferSubmissionCommand("Limit", "Chief Underwriting Officer",
                        "Senior UW - Casualty", "Needs sign-off")).body();

        assertThat(referral.referralReason()).isEqualTo("Limit");
        assertThat(referral.referralAuthority()).isEqualTo("Chief Underwriting Officer");
        assertThat(referral.referredTo()).isEqualTo("Senior UW - Casualty");
        assertThat(referral.referralNotes()).isEqualTo("Needs sign-off");
        assertThat(referral.referralDecision()).isEqualTo("PENDING");
        assertThat(referral.tenantId()).isEqualTo(TENANT);
        server.verify();
    }

    @Test
    void mapsTheSnakeCaseReferralDecisionFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(DECISION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "submissionId": "%s",
                                  "referral_decision": "APPROVED",
                                  "referral_decided_by": "Chief Underwriter",
                                  "referral_decided_at": "2026-10-22T12:00:00Z",
                                  "version": 2
                                }
                                """.formatted(SUBMISSION_ID)));

        SubmissionDto referral = client.decideSubmissionReferral(TENANT, SUBMISSION_ID,
                new DecideReferralCommand("APPROVED", "Chief Underwriter", null)).body();

        assertThat(referral.referralDecision()).isEqualTo("APPROVED");
        assertThat(referral.referralDecidedBy()).isEqualTo("Chief Underwriter");
        assertThat(referral.referralDecidedAt())
                .isEqualTo(Instant.parse("2026-10-22T12:00:00Z"));
        server.verify();
    }

    @Test
    void honoursAnExplicitReplayMarker() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(REFERRAL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotent-Replayed", "true")
                        .body(submissionJson("REFERRED", 1, "PENDING", null, null)));

        Mutation<SubmissionDto> result = client.referSubmission(TENANT, SUBMISSION_ID, REFER);

        assertThat(result.replayed()).isTrue();
        server.verify();
    }

    @Test
    void preservesTheExpertConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(DECISION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "status": 409,
                                  "detail": "Submission has no pending referral decision.",
                                  "code": "REFERRAL_NOT_PENDING",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "meta": {"submissionId": "%s",
                                           "referralDecision": "APPROVED"}
                                }
                                """.formatted(SUBMISSION_ID)));

        assertThatThrownBy(() -> client.decideSubmissionReferral(TENANT, SUBMISSION_ID, DECIDE))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("REFERRAL_NOT_PENDING");
                    assertThat(api.meta()).containsEntry("referralDecision", "APPROVED");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(REFERRAL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "status": 404,
                                  "detail": "Underwriting submission not found",
                                  "code": "SUBMISSION_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """));

        assertThatThrownBy(() -> client.referSubmission(TENANT, SUBMISSION_ID, REFER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("SUBMISSION_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertReferralResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(REFERRAL + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.referSubmission(TENANT, SUBMISSION_ID, REFER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    @Test
    void readsTheReferralFromTheSubmissionProjection() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson("REFERRED", 2, "APPROVED",
                                "Chief Underwriter", Instant.parse("2026-10-22T12:00:00Z"))));

        SubmissionDto submission = client.findSubmission(TENANT, SUBMISSION_ID);

        assertReferral(submission, "APPROVED", "Chief Underwriter",
                Instant.parse("2026-10-22T12:00:00Z"), 2);
        server.verify();
    }

    @Test
    void mapsAPreReferralSubmissionWithoutReferralMembers() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "submissionId": "%s",
                                  "submissionNumber": "SUB-2026-0000001",
                                  "status": "RECEIVED",
                                  "version": 0
                                }
                                """.formatted(SUBMISSION_ID)));

        SubmissionDto submission = client.findSubmission(TENANT, SUBMISSION_ID);

        assertThat(submission.referralReason()).isNull();
        assertThat(submission.referralAuthority()).isNull();
        assertThat(submission.referredTo()).isNull();
        assertThat(submission.referralNotes()).isNull();
        assertThat(submission.referralDecision()).isNull();
        assertThat(submission.referralDecidedBy()).isNull();
        assertThat(submission.referralDecidedAt()).isNull();
        server.verify();
    }

    private static void assertReferral(SubmissionDto submission, String decision,
                                       String decidedBy, Instant decidedAt, int version) {
        assertThat(submission.submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(submission.status()).isEqualTo(
                "DECLINED".equals(decision) ? "DECLINED" : "REFERRED");
        assertThat(submission.referralReason()).isEqualTo("Aggregate limit above authority");
        assertThat(submission.referralAuthority()).isEqualTo("Chief Underwriting Officer");
        assertThat(submission.referredTo()).isEqualTo("Senior UW - Casualty");
        assertThat(submission.referralNotes()).isEqualTo("Needs reinsurance sign-off");
        assertThat(submission.referralDecision()).isEqualTo(decision);
        assertThat(submission.referralDecidedBy()).isEqualTo(decidedBy);
        assertThat(submission.referralDecidedAt()).isEqualTo(decidedAt);
        assertThat(submission.version()).isEqualTo(version);
    }

    private static String submissionJson(String status, int version, String decision,
                                         String decidedBy, Instant decidedAt) {
        String decidedAtJson = decidedAt == null ? "null" : "\"" + decidedAt + "\"";
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
                  "referralReason": "Aggregate limit above authority",
                  "referralAuthority": "Chief Underwriting Officer",
                  "referredTo": "Senior UW - Casualty",
                  "referralNotes": "Needs reinsurance sign-off",
                  "referralDecision": "%s",
                  "referralDecidedBy": %s,
                  "referralDecidedAt": %s,
                  "version": %d,
                  "createdAt": "2026-10-22T09:00:00Z",
                  "updatedAt": "2026-10-22T10:00:00Z"
                }
                """.formatted(SUBMISSION_ID, TENANT, APPLICANT_ID, status, decision,
                decidedBy == null ? "null" : "\"" + decidedBy + "\"", decidedAtJson, version);
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

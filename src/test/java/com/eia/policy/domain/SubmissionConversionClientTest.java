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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SubmissionConversionClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final String BASE = "http://expert";
    private static final String SUBMISSIONS = BASE + "/internal/v1/policies/submissions";
    private static final String SUBMISSION = SUBMISSIONS + "/" + SUBMISSION_ID;
    private static final String CONVERT = SUBMISSION + "/convert";

    @Test
    void convertsTheSubmissionForwardingTenantDirectIdempotencyAndTerms() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("Idempotency-Key", "convert-key"))
                .andExpect(content().json("""
                        {
                          "premiumAmount": 12500.0000,
                          "premiumCurrency": "USD",
                          "expirationDate": "2027-01-01"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"0\"")
                        .header("Idempotent-Replayed", "false")
                        .body(conversionJson()));

        Mutation<SubmissionConversionResult> mutation =
                client.convertSubmission(TENANT, SUBMISSION_ID, command(), "convert-key");

        assertThat(mutation.etag()).isEqualTo("\"0\"");
        assertThat(mutation.replayed()).isFalse();
        assertThat(mutation.body().submission().submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(mutation.body().submission().policyId()).isEqualTo(POLICY_ID);
        assertThat(mutation.body().submission().status()).isEqualTo("BOUND");
        assertThat(mutation.body().policy().id()).isEqualTo(POLICY_ID);
        assertThat(mutation.body().policy().policyNumber()).isEqualTo("POL-2026-0000001");
        assertThat(mutation.body().policy().premiumAmount())
                .isEqualByComparingTo("12500.0000");
        server.verify();
    }

    @Test
    void omitsTheExpirationDateAndIdempotencyKeyWhenNotSupplied() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("""
                        {
                          "premiumAmount": 12500.0000,
                          "premiumCurrency": "USD"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotent-Replayed", "true")
                        .body(conversionJson()));

        Mutation<SubmissionConversionResult> mutation = client.convertSubmission(TENANT,
                SUBMISSION_ID, new ConvertSubmissionCommand(
                        new BigDecimal("12500.0000"), "USD", null), null);

        assertThat(mutation.replayed()).isTrue();
        assertThat(mutation.body().policy().id()).isEqualTo(POLICY_ID);
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-submission",
                                  "title": "Invalid submission",
                                  "status": 400,
                                  "detail": "premiumAmount must not be negative",
                                  "code": "INVALID_SUBMISSION",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "premiumAmount", "code": "MIN_VALUE",
                                     "message": "premiumAmount must be non-negative"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.convertSubmission(TENANT, SUBMISSION_ID, command(),
                "convert-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_SUBMISSION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("premiumAmount"));
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/submission-not-found",
                                  "title": "Submission not found",
                                  "status": 404,
                                  "detail": "Underwriting submission not found: %s",
                                  "code": "SUBMISSION_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(SUBMISSION_ID)));

        assertThatThrownBy(() -> client.convertSubmission(TENANT, SUBMISSION_ID, command(), null))
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

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/submission-already-converted",
                                  "title": "Submission already converted",
                                  "status": 409,
                                  "detail": "Submission %s already converted to policy %s",
                                  "code": "SUBMISSION_ALREADY_CONVERTED",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "meta": {"policyId": "%s"}
                                }
                                """.formatted(SUBMISSION_ID, POLICY_ID, POLICY_ID)));

        assertThatThrownBy(() -> client.convertSubmission(TENANT, SUBMISSION_ID, command(),
                "convert-key"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("SUBMISSION_ALREADY_CONVERTED");
                    assertThat(api.meta()).containsEntry("policyId", POLICY_ID.toString());
                });
        server.verify();
    }

    @Test
    void composesTheEnvelopeWhenTheExpertReturnsOnlyTheCreatedPolicy() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"1\"")
                        .header("Idempotent-Replayed", "false")
                        .body(policyJson()));
        server.expect(requestTo(SUBMISSION + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(submissionJson(true)));

        Mutation<SubmissionConversionResult> mutation =
                client.convertSubmission(TENANT, SUBMISSION_ID, command(), "convert-key");

        assertThat(mutation.etag()).isEqualTo("\"1\"");
        assertThat(mutation.replayed()).isFalse();
        assertThat(mutation.body().submission().submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(mutation.body().submission().policyId()).isEqualTo(POLICY_ID);
        assertThat(mutation.body().policy().id()).isEqualTo(POLICY_ID);
        server.verify();
    }

    @Test
    void rejectsAConversionEnvelopeWithoutAPolicy() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"submission": %s}
                                """.formatted(submissionJson(true))));

        assertThatThrownBy(() -> client.convertSubmission(TENANT, SUBMISSION_ID, command(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    @Test
    void leavesThePolicyLinkNullWhenTheExpertDoesNotExposeIt() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(conversionJson(false)));

        SubmissionConversionResult result = client.convertSubmission(TENANT, SUBMISSION_ID,
                command(), null).body();

        assertThat(result.submission().policyId()).isNull();
        assertThat(result.policy().id()).isEqualTo(POLICY_ID);
        server.verify();
    }

    @Test
    void mapsTheSpecColumnAliasesOnAConvertedSubmission() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(CONVERT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "submission": {
                                    "id": "%s",
                                    "submission_number": "SUB-2026-0000001",
                                    "tenant_id": "%s",
                                    "applicant_party_id": "%s",
                                    "product_code": "CGL_PRODUCTS",
                                    "requested_effective_date": "2026-01-01",
                                    "requested_limit_amount": 1000000.0000,
                                    "requested_limit_currency": "USD",
                                    "tort_codes": ["ASBESTOS"],
                                    "status": "BOUND",
                                    "version": 3,
                                    "created_at": "2026-10-22T09:00:00Z",
                                    "updated_at": "2026-10-22T10:00:00Z",
                                    "policy_id": "%s"
                                  },
                                  "policy": %s
                                }
                                """.formatted(SUBMISSION_ID, TENANT, APPLICANT_ID, POLICY_ID,
                                policyJson())));

        SubmissionConversionResult result = client.convertSubmission(TENANT, SUBMISSION_ID,
                command(), null).body();

        assertThat(result.submission().submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(result.submission().submissionNumber()).isEqualTo("SUB-2026-0000001");
        assertThat(result.submission().policyId()).isEqualTo(POLICY_ID);
        assertThat(result.submission().status()).isEqualTo("BOUND");
        assertThat(result.policy().id()).isEqualTo(POLICY_ID);
        server.verify();
    }

    private static ConvertSubmissionCommand command() {
        return new ConvertSubmissionCommand(new BigDecimal("12500.0000"), "USD",
                LocalDate.of(2027, 1, 1));
    }

    private static String conversionJson() {
        return conversionJson(true);
    }

    private static String conversionJson(boolean includePolicyId) {
        return """
                {
                  "submission": %s,
                  "policy": %s
                }
                """.formatted(submissionJson(includePolicyId), policyJson());
    }

    private static String submissionJson(boolean includePolicyId) {
        String policyMember = includePolicyId
                ? "\"policyId\": \"" + POLICY_ID + "\"," : "";
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
                  "status": "BOUND",
                  "version": 3,
                  %s
                  "createdAt": "2026-10-22T09:00:00Z",
                  "updatedAt": "2026-10-22T10:00:00Z"
                }
                """.formatted(SUBMISSION_ID, TENANT, APPLICANT_ID, policyMember);
    }

    private static String policyJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyNumber": "POL-2026-0000001",
                  "productCode": "CGL_PRODUCTS",
                  "insuredPartyId": "%s",
                  "status": "ISSUED",
                  "effectiveDate": "2026-01-01",
                  "expirationDate": "2027-01-01",
                  "premiumAmount": 12500.0000,
                  "premiumCurrency": "USD",
                  "limitAmount": 1000000.0000,
                  "limitCurrency": "USD",
                  "version": 0,
                  "createdAt": "2026-10-22T11:00:00Z",
                  "updatedAt": "2026-10-22T11:00:00Z"
                }
                """.formatted(POLICY_ID, TENANT, APPLICANT_ID);
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

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
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SubmissionReferralHistoryClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID REFERRAL_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SECOND_REFERRAL_ID =
            UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert";
    private static final String SUBMISSIONS = BASE + "/internal/v1/policies/submissions";
    private static final String HISTORY = SUBMISSIONS + "/" + SUBMISSION_ID + "/referrals";
    private static final Instant OPENED_AT = Instant.parse("2026-10-21T09:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-22T12:00:00Z");

    @Test
    void listsTheReferralHistoryForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [
                                  {
                                    "referralId": "%s",
                                    "submissionId": "%s",
                                    "reason": "LIMIT",
                                    "authority": "Chief Underwriting Officer",
                                    "referredTo": "Senior UW - Casualty",
                                    "notes": "Approved up to the treaty capacity",
                                    "decision": "APPROVED",
                                    "decidedBy": "Chief Underwriter",
                                    "decidedAt": "%s",
                                    "createdAt": "2026-10-22T09:00:00Z"
                                  },
                                  {
                                    "referralId": "%s",
                                    "submissionId": "%s",
                                    "reason": "PREMIUM",
                                    "authority": "Chief Underwriting Officer",
                                    "referredTo": "Senior UW - Property",
                                    "notes": "Premium above authority",
                                    "decision": "PENDING",
                                    "decidedBy": null,
                                    "decidedAt": null,
                                    "createdAt": "%s"
                                  }
                                ]
                                """.formatted(SECOND_REFERRAL_ID, SUBMISSION_ID, DECIDED_AT,
                                REFERRAL_ID, SUBMISSION_ID, OPENED_AT)));

        List<ReferralDto> history = client.findSubmissionReferrals(TENANT, SUBMISSION_ID);

        assertThat(history).hasSize(2);
        ReferralDto approved = history.get(0);
        assertThat(approved.referralId()).isEqualTo(SECOND_REFERRAL_ID);
        assertThat(approved.submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(approved.reason()).isEqualTo("LIMIT");
        assertThat(approved.authority()).isEqualTo("Chief Underwriting Officer");
        assertThat(approved.referredTo()).isEqualTo("Senior UW - Casualty");
        assertThat(approved.notes()).isEqualTo("Approved up to the treaty capacity");
        assertThat(approved.decision()).isEqualTo("APPROVED");
        assertThat(approved.decidedBy()).isEqualTo("Chief Underwriter");
        assertThat(approved.decidedAt()).isEqualTo(DECIDED_AT);
        assertThat(approved.createdAt()).isEqualTo(Instant.parse("2026-10-22T09:00:00Z"));

        ReferralDto pending = history.get(1);
        assertThat(pending.referralId()).isEqualTo(REFERRAL_ID);
        assertThat(pending.reason()).isEqualTo("PREMIUM");
        assertThat(pending.decision()).isEqualTo("PENDING");
        assertThat(pending.notes()).isEqualTo("Premium above authority");
        assertThat(pending.decidedBy()).isNull();
        assertThat(pending.decidedAt()).isNull();
        assertThat(pending.createdAt()).isEqualTo(OPENED_AT);
        server.verify();
    }

    @Test
    void acceptsAnItemsEnvelopeAndMapsTheSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "items": [
                                    {
                                      "id": "%s",
                                      "submission_id": "%s",
                                      "referral_reason": "TORT",
                                      "required_authority": "Chief Underwriting Officer",
                                      "assigned_to": "Senior UW - Casualty",
                                      "comments": "New tort",
                                      "referral_decision": "DECLINED",
                                      "referral_decided_by": "Chief Underwriter",
                                      "referral_decided_at": "%s",
                                      "created_at": "%s"
                                    }
                                  ]
                                }
                                """.formatted(REFERRAL_ID, SUBMISSION_ID, DECIDED_AT, OPENED_AT)));

        List<ReferralDto> history = client.findSubmissionReferrals(TENANT, SUBMISSION_ID);

        assertThat(history).singleElement().satisfies(referral -> {
            assertThat(referral.referralId()).isEqualTo(REFERRAL_ID);
            assertThat(referral.submissionId()).isEqualTo(SUBMISSION_ID);
            assertThat(referral.reason()).isEqualTo("TORT");
            assertThat(referral.authority()).isEqualTo("Chief Underwriting Officer");
            assertThat(referral.referredTo()).isEqualTo("Senior UW - Casualty");
            assertThat(referral.notes()).isEqualTo("New tort");
            assertThat(referral.decision()).isEqualTo("DECLINED");
            assertThat(referral.decidedBy()).isEqualTo("Chief Underwriter");
            assertThat(referral.decidedAt()).isEqualTo(DECIDED_AT);
            assertThat(referral.createdAt()).isEqualTo(OPENED_AT);
        });
        server.verify();
    }

    @Test
    void acceptsAReferralsEnvelope() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "referrals": [
                                    {
                                      "referralId": "%s",
                                      "submissionId": "%s",
                                      "reason": "APPETITE",
                                      "authority": "Chief Underwriting Officer",
                                      "referredTo": "Senior UW - Casualty",
                                      "decision": "PENDING",
                                      "createdAt": "%s"
                                    }
                                  ]
                                }
                                """.formatted(REFERRAL_ID, SUBMISSION_ID, OPENED_AT)));

        List<ReferralDto> history = client.findSubmissionReferrals(TENANT, SUBMISSION_ID);

        assertThat(history).singleElement().satisfies(referral -> {
            assertThat(referral.referralId()).isEqualTo(REFERRAL_ID);
            assertThat(referral.reason()).isEqualTo("APPETITE");
            assertThat(referral.decision()).isEqualTo("PENDING");
            assertThat(referral.notes()).isNull();
            assertThat(referral.decidedBy()).isNull();
            assertThat(referral.decidedAt()).isNull();
        });
        server.verify();
    }

    @Test
    void returnsAnEmptyListWhenTheSubmissionHasNoReferralHistory() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[]"));

        assertThat(client.findSubmissionReferrals(TENANT, SUBMISSION_ID)).isEmpty();
        server.verify();
    }

    @Test
    void fillsTheSubmissionIdWhenTheExpertRowOmitsIt() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [
                                  {
                                    "referralId": "%s",
                                    "reason": "LIMIT",
                                    "decision": "PENDING",
                                    "createdAt": "%s"
                                  }
                                ]
                                """.formatted(REFERRAL_ID, OPENED_AT)));

        List<ReferralDto> history = client.findSubmissionReferrals(TENANT, SUBMISSION_ID);

        assertThat(history).singleElement()
                .satisfies(referral -> assertThat(referral.submissionId())
                        .isEqualTo(SUBMISSION_ID));
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertHistoryResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findSubmissionReferrals(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    @Test
    void rejectsAnExpertHistoryThatIsNotAnArray() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"referrals\":{}}"));

        assertThatThrownBy(() -> client.findSubmissionReferrals(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    @Test
    void rejectsAReferralRowWithoutAnIdentity() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [{"submissionId": "%s", "decision": "PENDING"}]
                                """.formatted(SUBMISSION_ID)));

        assertThatThrownBy(() -> client.findSubmissionReferrals(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("EXPERT_BAD_RESPONSE"));
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(HISTORY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "status": 404,
                                  "detail": "Underwriting submission not found",
                                  "code": "SUBMISSION_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false,
                                  "meta": {"submissionId": "%s"}
                                }
                                """.formatted(SUBMISSION_ID)));

        assertThatThrownBy(() -> client.findSubmissionReferrals(TENANT, SUBMISSION_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("SUBMISSION_NOT_FOUND");
                    assertThat(api.meta()).containsEntry("submissionId", SUBMISSION_ID.toString());
                });
        server.verify();
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

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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifies the expert contract behind installment dunning: the tenant and
 * DIRECT book headers, the action payload, the newest-first history
 * projection, and the preserved {@code 400}/{@code 404}/{@code 409} problems.
 */
class PolicyDunningClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID DUNNING_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final UUID SECOND_DUNNING_ID =
            UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final String BASE = "http://expert";
    private static final String DUNNING = BASE + "/internal/v1/billing/installments/"
            + INSTALLMENT_ID + "/dunning";

    @Test
    void recordsADunningActionAndForwardsTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"level": "FIRST_NOTICE", "channel": "EMAIL",
                         "notes": "Lapse warning sent to the broker"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(dunningJson()));

        DunningAction action = client.recordDunningAction(TENANT, INSTALLMENT_ID,
                new CreateDunningActionCommand("FIRST_NOTICE", "EMAIL",
                        "Lapse warning sent to the broker"));

        assertThat(action.dunningId()).isEqualTo(DUNNING_ID);
        assertThat(action.installmentId()).isEqualTo(INSTALLMENT_ID);
        assertThat(action.planId()).isEqualTo(PLAN_ID);
        assertThat(action.policyId()).isEqualTo(POLICY_ID);
        assertThat(action.level()).isEqualTo("FIRST_NOTICE");
        assertThat(action.channel()).isEqualTo("EMAIL");
        assertThat(action.notes()).isEqualTo("Lapse warning sent to the broker");
        assertThat(action.status()).isEqualTo("SENT");
        assertThat(action.sentAt()).isEqualTo(Instant.parse("2026-06-15T12:00:00Z"));
        server.verify();
    }

    @Test
    void listsTheDunningHistoryNewestFirstWithTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(dunningHistoryJson()));

        List<DunningAction> history = client.findDunningActions(TENANT, INSTALLMENT_ID);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(DunningAction::level)
                .containsExactly("SECOND_NOTICE", "FIRST_NOTICE");
        assertThat(history.get(0).dunningId()).isEqualTo(SECOND_DUNNING_ID);
        assertThat(history.get(0).channel()).isEqualTo("LETTER");
        assertThat(history.get(0).sentAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
        assertThat(history.get(1).dunningId()).isEqualTo(DUNNING_ID);
        assertThat(history.get(1).status()).isEqualTo("SENT");
        server.verify();
    }

    @Test
    void acceptsTheExpertRowIdentityAliasAndAbsentNotes() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [{"id": "%s", "installmentId": "%s", "planId": "%s",
                                  "policyId": "%s", "level": "REMINDER", "channel": "LETTER",
                                  "status": "SENT", "sentAt": "2026-06-15T12:00:00Z"}]
                                """.formatted(DUNNING_ID, INSTALLMENT_ID, PLAN_ID, POLICY_ID)));

        List<DunningAction> history = client.findDunningActions(TENANT, INSTALLMENT_ID);

        assertThat(history).singleElement().satisfies(action -> {
            assertThat(action.dunningId()).isEqualTo(DUNNING_ID);
            assertThat(action.notes()).isNull();
        });
        server.verify();
    }

    @Test
    void returnsAnEmptyHistoryWhenTheExpertHasNoAction() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[]"));

        assertThat(client.findDunningActions(TENANT, INSTALLMENT_ID)).isEmpty();
        server.verify();
    }

    @Test
    void preservesAValidationProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-dunning-level",
                                  "title": "Invalid dunning level",
                                  "status": 400,
                                  "detail": "level must be one of REMINDER, FIRST_NOTICE, SECOND_NOTICE, COLLECTIONS.",
                                  "code": "INVALID_DUNNING_LEVEL",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "level", "code": "INVALID_DUNNING_LEVEL",
                                     "message": "level must be one of REMINDER, FIRST_NOTICE, SECOND_NOTICE, COLLECTIONS."}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.recordDunningAction(TENANT, INSTALLMENT_ID,
                new CreateDunningActionCommand("DUNNING_9", "EMAIL", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_DUNNING_LEVEL");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("level");
                        assertThat(issue.code()).isEqualTo("INVALID_DUNNING_LEVEL");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheInstallmentNotFoundProblemFromTheExpertOnRecord() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/installment-not-found",
                                  "title": "Billing installment not found",
                                  "status": 404,
                                  "detail": "Installment not found: %s",
                                  "code": "INSTALLMENT_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(INSTALLMENT_ID)));

        assertThatThrownBy(() -> client.recordDunningAction(TENANT, INSTALLMENT_ID,
                new CreateDunningActionCommand("REMINDER", "EMAIL", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("INSTALLMENT_NOT_FOUND");
                    assertThat(api.getMessage()).contains(INSTALLMENT_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesTheInstallmentNotFoundProblemFromTheExpertOnHistory() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/installment-not-found",
                                  "title": "Billing installment not found",
                                  "status": 404,
                                  "detail": "Installment not found: %s",
                                  "code": "INSTALLMENT_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(INSTALLMENT_ID)));

        assertThatThrownBy(() -> client.findDunningActions(TENANT, INSTALLMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("INSTALLMENT_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void preservesTheDuplicateDunningConflictFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/dunning-already-sent",
                                  "title": "Dunning already sent",
                                  "status": 409,
                                  "detail": "A REMINDER dunning action already exists for installment %s.",
                                  "code": "DUNNING_ALREADY_SENT",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "level", "code": "DUNNING_ALREADY_SENT",
                                     "message": "A REMINDER dunning action already exists."}
                                  ],
                                  "meta": {"installmentId": "%s", "level": "REMINDER"}
                                }
                                """.formatted(INSTALLMENT_ID, INSTALLMENT_ID)));

        assertThatThrownBy(() -> client.recordDunningAction(TENANT, INSTALLMENT_ID,
                new CreateDunningActionCommand("REMINDER", "LETTER", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("DUNNING_ALREADY_SENT");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("DUNNING_ALREADY_SENT"));
                    assertThat(api.meta())
                            .containsEntry("installmentId", INSTALLMENT_ID.toString())
                            .containsEntry("level", "REMINDER");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertHistoryResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(DUNNING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findDunningActions(TENANT, INSTALLMENT_ID))
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
     * ISO instants; a plain builder would not know the Java time module.
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

    private static String dunningJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "installmentId": "%s",
                  "planId": "%s",
                  "policyId": "%s",
                  "level": "FIRST_NOTICE",
                  "channel": "EMAIL",
                  "notes": "Lapse warning sent to the broker",
                  "status": "SENT",
                  "sentAt": "2026-06-15T12:00:00Z",
                  "createdAt": "2026-06-15T12:00:00Z"
                }
                """.formatted(DUNNING_ID, TENANT, INSTALLMENT_ID, PLAN_ID, POLICY_ID);
    }

    private static String dunningHistoryJson() {
        return """
                [
                  {"id": "%s", "tenantId": "%s", "installmentId": "%s", "planId": "%s",
                   "policyId": "%s", "level": "SECOND_NOTICE", "channel": "LETTER",
                   "notes": "Final notice before collections", "status": "SENT",
                   "sentAt": "2026-07-01T09:00:00Z", "createdAt": "2026-07-01T09:00:00Z"},
                  {"id": "%s", "tenantId": "%s", "installmentId": "%s", "planId": "%s",
                   "policyId": "%s", "level": "FIRST_NOTICE", "channel": "EMAIL",
                   "notes": "Lapse warning sent to the broker", "status": "SENT",
                   "sentAt": "2026-06-15T12:00:00Z", "createdAt": "2026-06-15T12:00:00Z"}
                ]
                """.formatted(SECOND_DUNNING_ID, TENANT, INSTALLMENT_ID, PLAN_ID, POLICY_ID,
                DUNNING_ID, TENANT, INSTALLMENT_ID, PLAN_ID, POLICY_ID);
    }
}

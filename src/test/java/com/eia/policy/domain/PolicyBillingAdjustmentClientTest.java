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

/**
 * Wire tests for the positive premium billing adjustment: the internal path,
 * tenant/book context, the optional concurrency and idempotency headers, the
 * {@code {plan, installment, adjustmentAmount, newTotalPremium}} envelope, the
 * replay marker, and the preserved expert problems.
 */
class PolicyBillingAdjustmentClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final String BASE = "http://expert";
    private static final String ADJUSTMENT = BASE + "/internal/v1/policies/" + POLICY_ID
            + "/billing/adjustment";
    private static final BillingAdjustmentCommand COMMAND = new BillingAdjustmentCommand(
            LocalDate.of(2026, 5, 1), "exposure audit");

    @Test
    void postsTheAdjustmentAndMapsTheEnvelopeWithEveryHeader() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(header("If-Match", "\"4\""))
                .andExpect(header("Idempotency-Key", "adjust-key-1"))
                .andExpect(content().json("""
                        {"dueDate": "2026-05-01", "reason": "exposure audit"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"5\"")
                        .body(adjustmentJson()));

        Mutation<BillingAdjustmentResult> mutation = client.adjustBillingPlan(TENANT, POLICY_ID,
                COMMAND, "\"4\"", "adjust-key-1");

        assertThat(mutation.replayed()).isFalse();
        assertThat(mutation.etag()).isEqualTo("\"5\"");
        BillingAdjustmentResult result = mutation.body();
        assertThat(result.plan().planId()).isEqualTo(PLAN_ID);
        assertThat(result.plan().planNumber()).isEqualTo("BP-2026-0001");
        assertThat(result.plan().policyId()).isEqualTo(POLICY_ID);
        assertThat(result.plan().totalPremium()).isEqualByComparingTo("150000.0000");
        assertThat(result.plan().currency()).isEqualTo("USD");
        assertThat(result.plan().installmentCount()).isEqualTo(4);
        assertThat(result.plan().frequency()).isEqualTo("QUARTERLY");
        assertThat(result.plan().firstDueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.plan().status()).isEqualTo("ACTIVE");
        assertThat(result.plan().installments()).isEmpty();
        assertThat(result.installment().installmentId()).isEqualTo(INSTALLMENT_ID);
        assertThat(result.installment().installmentNumber()).isEqualTo(4);
        assertThat(result.installment().dueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.installment().amount()).isEqualByComparingTo("30000.0000");
        assertThat(result.installment().creditedAmount()).isEqualByComparingTo("0.0000");
        assertThat(result.installment().outstandingAmount()).isEqualByComparingTo("30000.0000");
        assertThat(result.installment().status()).isEqualTo("DUE");
        assertThat(result.installment().paidAt()).isNull();
        assertThat(result.installment().paymentId()).isNull();
        assertThat(result.adjustmentAmount()).isEqualByComparingTo("30000.0000");
        assertThat(result.newTotalPremium()).isEqualByComparingTo("150000.0000");
        server.verify();
    }

    @Test
    void omitsTheOptionalHeadersWhenTheyAreAbsent() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(headerDoesNotExist("If-Match"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(adjustmentJson()));

        Mutation<BillingAdjustmentResult> mutation = client.adjustBillingPlan(TENANT, POLICY_ID,
                COMMAND, null, null);

        assertThat(mutation.replayed()).isFalse();
        assertThat(mutation.body().installment().installmentNumber()).isEqualTo(4);
        server.verify();
    }

    @Test
    void publishesAnExpertReplayAsReplayedWithAnOkStatus() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andExpect(header("Idempotency-Key", "adjust-key-1"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotent-Replayed", "true")
                        .header("ETag", "\"5\"")
                        .body(adjustmentJson()));

        Mutation<BillingAdjustmentResult> mutation = client.adjustBillingPlan(TENANT, POLICY_ID,
                COMMAND, null, "adjust-key-1");

        assertThat(mutation.replayed()).isTrue();
        assertThat(mutation.etag()).isEqualTo("\"5\"");
        assertThat(mutation.body().adjustmentAmount()).isEqualByComparingTo("30000.0000");
        server.verify();
    }

    @Test
    void derivesTheNewTotalPremiumWhenTheExpertOmitsIt() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(adjustmentJsonWithoutTotal()));

        BillingAdjustmentResult result = client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND,
                null, null).body();

        assertThat(result.newTotalPremium()).isEqualByComparingTo("150000.0000");
        assertThat(result.plan().totalPremium()).isEqualByComparingTo("150000.0000");
        server.verify();
    }

    @Test
    void preservesTheNoPremiumAdjustmentProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/no-premium-adjustment",
                                  "title": "No positive premium adjustment",
                                  "status": 400,
                                  "detail": "The policy premium does not exceed the billing plan total.",
                                  "code": "NO_PREMIUM_ADJUSTMENT",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "delta", "code": "NO_PREMIUM_ADJUSTMENT",
                                     "message": "A premium decrease must use a credit note."}
                                  ],
                                  "meta": {"delta": "0.0000"}
                                }
                                """));

        assertThatThrownBy(() -> client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("NO_PREMIUM_ADJUSTMENT");
                    assertThat(api.getMessage()).contains("does not exceed");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("delta");
                        assertThat(issue.code()).isEqualTo("NO_PREMIUM_ADJUSTMENT");
                    });
                    assertThat(api.meta()).containsEntry("delta", "0.0000");
                });
        server.verify();
    }

    @Test
    void preservesTheMissingBillingPlanProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/billing-plan-not-found",
                                  "title": "Billing plan not found",
                                  "status": 404,
                                  "detail": "No billing plan exists for policy %s",
                                  "code": "BILLING_PLAN_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("BILLING_PLAN_NOT_FOUND");
                    assertThat(api.getMessage()).contains(POLICY_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesTheInactivePlanConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/billing-plan-not-active",
                                  "title": "Billing plan is not active",
                                  "status": 409,
                                  "detail": "Billing plan %s is CANCELLED; only an ACTIVE plan can be adjusted.",
                                  "code": "BILLING_PLAN_NOT_ACTIVE",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "meta": {"planId": "%s", "currentStatus": "CANCELLED"}
                                }
                                """.formatted(PLAN_ID, PLAN_ID)));

        assertThatThrownBy(() -> client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("BILLING_PLAN_NOT_ACTIVE");
                    assertThat(api.meta())
                            .containsEntry("planId", PLAN_ID.toString())
                            .containsEntry("currentStatus", "CANCELLED");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertAdjustmentResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ADJUSTMENT + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND, null, null))
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

    private static String adjustmentJson() {
        return """
                {
                  "plan": {
                    "id": "%s",
                    "tenantId": "%s",
                    "policyId": "%s",
                    "planNumber": "BP-2026-0001",
                    "totalPremium": "150000.0000",
                    "currency": "USD",
                    "installmentCount": 4,
                    "frequency": "QUARTERLY",
                    "firstDueDate": "2026-01-15",
                    "status": "ACTIVE",
                    "createdAt": "2026-01-01T00:00:00Z"
                  },
                  "installment": {
                    "id": "%s",
                    "tenantId": "%s",
                    "planId": "%s",
                    "installmentNumber": 4,
                    "dueDate": "2026-05-01",
                    "amount": "30000.0000",
                    "creditedAmount": "0.0000",
                    "outstandingAmount": "30000.0000",
                    "status": "DUE",
                    "paidAt": null,
                    "paymentId": null,
                    "createdAt": "2026-10-22T09:00:00Z"
                  },
                  "adjustmentAmount": "30000.0000",
                  "newTotalPremium": "150000.0000"
                }
                """.formatted(PLAN_ID, TENANT, POLICY_ID, INSTALLMENT_ID, TENANT, PLAN_ID);
    }

    private static String adjustmentJsonWithoutTotal() {
        return """
                {
                  "plan": {
                    "id": "%s",
                    "policyId": "%s",
                    "planNumber": "BP-2026-0001",
                    "totalPremium": "150000.0000",
                    "currency": "USD",
                    "installmentCount": 4,
                    "frequency": "QUARTERLY",
                    "firstDueDate": "2026-01-15",
                    "status": "ACTIVE"
                  },
                  "installment": {
                    "id": "%s",
                    "installmentNumber": 4,
                    "dueDate": "2026-05-01",
                    "amount": "30000.0000",
                    "creditedAmount": "0.0000",
                    "outstandingAmount": "30000.0000",
                    "status": "DUE"
                  },
                  "adjustmentAmount": "30000.0000"
                }
                """.formatted(PLAN_ID, POLICY_ID, INSTALLMENT_ID);
    }
}

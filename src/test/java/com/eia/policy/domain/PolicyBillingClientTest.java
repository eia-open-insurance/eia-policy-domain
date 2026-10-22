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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PolicyBillingClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PAYMENT_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert";
    private static final String BILLING = BASE + "/internal/v1/policies/" + POLICY_ID + "/billing";

    @Test
    void readsTheBillingPlanWithInstallmentsAndForwardsTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(planJson()));

        BillingPlan plan = client.findBillingPlan(TENANT, POLICY_ID);

        assertThat(plan.planId()).isEqualTo(PLAN_ID);
        assertThat(plan.planNumber()).isEqualTo("BP-2026-0001");
        assertThat(plan.policyId()).isEqualTo(POLICY_ID);
        assertThat(plan.totalPremium()).isEqualByComparingTo("12000.0000");
        assertThat(plan.currency()).isEqualTo("USD");
        assertThat(plan.installmentCount()).isEqualTo(2);
        assertThat(plan.frequency()).isEqualTo("SEMI_ANNUAL");
        assertThat(plan.firstDueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(plan.status()).isEqualTo("ACTIVE");
        assertThat(plan.installments()).hasSize(2).first().satisfies(installment -> {
            assertThat(installment.installmentId()).isEqualTo(INSTALLMENT_ID);
            assertThat(installment.installmentNumber()).isEqualTo(1);
            assertThat(installment.dueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(installment.amount()).isEqualByComparingTo("6000.0000");
            assertThat(installment.creditedAmount()).isEqualByComparingTo("0.0000");
            assertThat(installment.outstandingAmount()).isEqualByComparingTo("0.0000");
            assertThat(installment.status()).isEqualTo("PAID");
            assertThat(installment.paidAt()).isEqualTo(Instant.parse("2026-01-15T12:00:00Z"));
            assertThat(installment.paymentId()).isEqualTo(PAYMENT_ID);
        });
        assertThat(plan.installments().get(1).status()).isEqualTo("PENDING");
        assertThat(plan.installments().get(1).creditedAmount()).isEqualByComparingTo("1000.0000");
        assertThat(plan.installments().get(1).outstandingAmount())
                .isEqualByComparingTo("5000.0000");
        assertThat(plan.installments().get(1).paidAt()).isNull();
        assertThat(plan.summary()).satisfies(summary -> {
            assertThat(summary.totalAmount()).isEqualByComparingTo("12000.0000");
            assertThat(summary.paidAmount()).isEqualByComparingTo("6000.0000");
            assertThat(summary.waivedAmount()).isEqualByComparingTo("0");
            assertThat(summary.creditedAmount()).isEqualByComparingTo("1000.0000");
            assertThat(summary.outstandingAmount()).isEqualByComparingTo("5000.0000");
            assertThat(summary.overdueAmount()).isEqualByComparingTo("0");
            assertThat(summary.currency()).isEqualTo("USD");
            assertThat(summary.installmentCount()).isEqualTo(2);
        });
        server.verify();
    }

    @Test
    void derivesTheBillingSummaryWhenTheExpertOmitsIt() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "plan": {"id": "%s", "policyId": "%s",
                                    "planNumber": "BP-2026-0001", "totalPremium": "12000.0000",
                                    "currency": "USD", "installmentCount": 2,
                                    "frequency": "SEMI_ANNUAL", "firstDueDate": "2026-01-15",
                                    "status": "ACTIVE"},
                                  "installments": [
                                    {"id": "%s", "installmentNumber": 1,
                                     "dueDate": "2026-01-15", "amount": "6000.0000",
                                     "status": "PAID", "creditedAmount": "0.0000",
                                     "outstandingAmount": "0.0000",
                                     "paidAt": "2026-01-15T12:00:00Z",
                                     "paymentId": "%s"},
                                    {"id": "dddddddd-dddd-7ddd-8ddd-dddddddddddd",
                                     "installmentNumber": 2, "dueDate": "2026-07-15",
                                     "amount": "6000.0000", "status": "DUE",
                                     "creditedAmount": "1500.0000",
                                     "outstandingAmount": "4500.0000"}
                                  ]
                                }
                                """.formatted(PLAN_ID, POLICY_ID, INSTALLMENT_ID, PAYMENT_ID)));

        BillingPlan plan = client.findBillingPlan(TENANT, POLICY_ID);

        assertThat(plan.summary()).satisfies(summary -> {
            assertThat(summary.totalAmount()).isEqualByComparingTo("12000.0000");
            assertThat(summary.paidAmount()).isEqualByComparingTo("6000.0000");
            assertThat(summary.creditedAmount()).isEqualByComparingTo("1500.0000");
            assertThat(summary.outstandingAmount()).isEqualByComparingTo("4500.0000");
            assertThat(summary.installmentCount()).isEqualTo(2);
        });
        server.verify();
    }

    @Test
    void restoresTheCreditedPremiumWhenACreditNoteIsReversed() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(reversalPlanJson()));

        BillingPlan plan = client.findBillingPlan(TENANT, POLICY_ID);

        assertThat(plan.installments()).hasSize(2);
        assertThat(plan.installments().get(1).status()).isEqualTo("DUE");
        assertThat(plan.installments().get(1).creditedAmount()).isEqualByComparingTo("0.0000");
        assertThat(plan.installments().get(1).outstandingAmount())
                .isEqualByComparingTo("6000.0000");
        assertThat(plan.summary()).satisfies(summary -> {
            assertThat(summary.totalAmount()).isEqualByComparingTo("12000.0000");
            assertThat(summary.creditedAmount()).isEqualByComparingTo("0");
            assertThat(summary.outstandingAmount()).isEqualByComparingTo("6000.0000");
            assertThat(summary.installmentCount()).isEqualTo(2);
        });
        server.verify();
    }

    @Test
    void postsTheCreatePayloadWithTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"installmentCount": 2, "frequency": "SEMI_ANNUAL",
                         "firstDueDate": "2026-01-15"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(planJson()));

        BillingPlan plan = client.createBillingPlan(TENANT, POLICY_ID,
                new CreateBillingPlanCommand(2, "SEMI_ANNUAL", LocalDate.of(2026, 1, 15)));

        assertThat(plan.planNumber()).isEqualTo("BP-2026-0001");
        assertThat(plan.installments()).hasSize(2);
        server.verify();
    }

    @Test
    void patchesAnInstallmentWithTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "/installments/" + INSTALLMENT_ID
                        + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"status": "PAID", "paymentId": "%s"}
                        """.formatted(PAYMENT_ID)))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"id": "%s", "tenantId": "%s", "planId": "%s",
                                 "installmentNumber": 1, "dueDate": "2026-01-15",
                                 "amount": "6000.0000", "creditedAmount": "500.0000",
                                 "outstandingAmount": "0.0000", "status": "PAID",
                                 "paidAt": "2026-01-15T12:00:00Z", "paymentId": "%s",
                                 "createdAt": "2026-01-01T00:00:00Z"}
                                """.formatted(INSTALLMENT_ID, TENANT, PLAN_ID, PAYMENT_ID)));

        Installment installment = client.updateInstallment(TENANT, POLICY_ID, INSTALLMENT_ID,
                new UpdateInstallmentCommand("PAID", PAYMENT_ID));

        assertThat(installment.status()).isEqualTo("PAID");
        assertThat(installment.creditedAmount()).isEqualByComparingTo("500.0000");
        assertThat(installment.outstandingAmount()).isEqualByComparingTo("0.0000");
        assertThat(installment.paidAt()).isEqualTo(Instant.parse("2026-01-15T12:00:00Z"));
        assertThat(installment.paymentId()).isEqualTo(PAYMENT_ID);
        server.verify();
    }

    @Test
    void preservesAValidationProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-billing-frequency",
                                  "title": "Invalid billing frequency",
                                  "status": 400,
                                  "detail": "frequency must be ANNUAL or SEMI_ANNUAL for this product",
                                  "code": "INVALID_BILLING_FREQUENCY",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "frequency", "code": "INVALID_FORMAT",
                                     "message": "is not supported by the product"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createBillingPlan(TENANT, POLICY_ID,
                new CreateBillingPlanCommand(2, "WEEKLY", LocalDate.of(2026, 1, 15))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_BILLING_FREQUENCY");
                    assertThat(api.getMessage()).contains("frequency must be");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("frequency");
                        assertThat(issue.code()).isEqualTo("INVALID_FORMAT");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheBillingPlanNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
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

        assertThatThrownBy(() -> client.findBillingPlan(TENANT, POLICY_ID))
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
    void preservesTheInstallmentConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "/installments/" + INSTALLMENT_ID
                        + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/installment-already-settled",
                                  "title": "Installment already settled",
                                  "status": 409,
                                  "detail": "Installment 1 is already PAID",
                                  "code": "INSTALLMENT_ALREADY_SETTLED",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "status", "code": "INSTALLMENT_ALREADY_SETTLED",
                                     "message": "Installment is already PAID."}
                                  ],
                                  "meta": {"installmentId": "%s", "currentStatus": "PAID"}
                                }
                                """.formatted(INSTALLMENT_ID)));

        assertThatThrownBy(() -> client.updateInstallment(TENANT, POLICY_ID, INSTALLMENT_ID,
                new UpdateInstallmentCommand("PAID", PAYMENT_ID)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("INSTALLMENT_ALREADY_SETTLED");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("INSTALLMENT_ALREADY_SETTLED"));
                    assertThat(api.meta())
                            .containsEntry("installmentId", INSTALLMENT_ID.toString())
                            .containsEntry("currentStatus", "PAID");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertBillingResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BILLING + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findBillingPlan(TENANT, POLICY_ID))
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

    private static String planJson() {
        return """
                {
                  "plan": {
                    "id": "%s",
                    "tenantId": "%s",
                    "policyId": "%s",
                    "planNumber": "BP-2026-0001",
                    "totalPremium": "12000.0000",
                    "currency": "USD",
                    "installmentCount": 2,
                    "frequency": "SEMI_ANNUAL",
                    "firstDueDate": "2026-01-15",
                    "status": "ACTIVE",
                    "createdAt": "2026-01-01T00:00:00Z"
                  },
                  "installments": [
                    {"id": "%s", "tenantId": "%s", "planId": "%s",
                     "installmentNumber": 1, "dueDate": "2026-01-15",
                     "amount": "6000.0000", "creditedAmount": "0.0000",
                     "outstandingAmount": "0.0000", "status": "PAID",
                     "paidAt": "2026-01-15T12:00:00Z", "paymentId": "%s",
                     "createdAt": "2026-01-01T00:00:00Z"},
                    {"id": "dddddddd-dddd-7ddd-8ddd-dddddddddddd", "tenantId": "%s",
                     "planId": "%s", "installmentNumber": 2, "dueDate": "2026-07-15",
                     "amount": "6000.0000", "creditedAmount": "1000.0000",
                     "outstandingAmount": "5000.0000", "status": "PENDING", "paidAt": null,
                     "paymentId": null, "createdAt": "2026-01-01T00:00:00Z"}
                  ],
                  "summary": {
                    "totalAmount": "12000.0000",
                    "paidAmount": "6000.0000",
                    "waivedAmount": "0.0000",
                    "creditedAmount": "1000.0000",
                    "outstandingAmount": "5000.0000",
                    "overdueAmount": "0.0000",
                    "currency": "USD",
                    "installmentCount": 2
                  }
                }
                """.formatted(PLAN_ID, TENANT, POLICY_ID, INSTALLMENT_ID, TENANT, PLAN_ID,
                PAYMENT_ID, TENANT, PLAN_ID);
    }

    private static String reversalPlanJson() {
        return """
                {
                  "plan": {
                    "id": "%s",
                    "tenantId": "%s",
                    "policyId": "%s",
                    "planNumber": "BP-2026-0001",
                    "totalPremium": "12000.0000",
                    "currency": "USD",
                    "installmentCount": 2,
                    "frequency": "SEMI_ANNUAL",
                    "firstDueDate": "2026-01-15",
                    "status": "ACTIVE",
                    "createdAt": "2026-01-01T00:00:00Z"
                  },
                  "installments": [
                    {"id": "%s", "tenantId": "%s", "planId": "%s",
                     "installmentNumber": 1, "dueDate": "2026-01-15",
                     "amount": "6000.0000", "creditedAmount": "0.0000",
                     "outstandingAmount": "0.0000", "status": "PAID",
                     "paidAt": "2026-01-15T12:00:00Z", "paymentId": "%s",
                     "createdAt": "2026-01-01T00:00:00Z"},
                    {"id": "dddddddd-dddd-7ddd-8ddd-dddddddddddd", "tenantId": "%s",
                     "planId": "%s", "installmentNumber": 2, "dueDate": "2026-07-15",
                     "amount": "6000.0000", "creditedAmount": "0.0000",
                     "outstandingAmount": "6000.0000", "status": "DUE", "paidAt": null,
                     "paymentId": null, "createdAt": "2026-01-01T00:00:00Z"}
                  ],
                  "summary": {
                    "totalAmount": "12000.0000",
                    "paidAmount": "6000.0000",
                    "waivedAmount": "0.0000",
                    "creditedAmount": "0.0000",
                    "outstandingAmount": "6000.0000",
                    "overdueAmount": "0.0000",
                    "currency": "USD",
                    "installmentCount": 2
                  }
                }
                """.formatted(PLAN_ID, TENANT, POLICY_ID, INSTALLMENT_ID, TENANT, PLAN_ID,
                PAYMENT_ID, TENANT, PLAN_ID);
    }
}

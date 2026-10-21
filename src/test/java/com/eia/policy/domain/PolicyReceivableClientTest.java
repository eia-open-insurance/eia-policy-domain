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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifies the expert contract behind the premium-receivable read: the tenant
 * and DIRECT book headers, the status/dueBefore/limit query, the public mapping,
 * and the preserved {@code 400}/{@code 404} problems.
 */
class PolicyReceivableClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SECOND_INSTALLMENT_ID =
            UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final UUID INSURED_PARTY_ID =
            UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final UUID PAYMENT_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert";
    private static final String RECEIVABLES = BASE + "/internal/v1/billing/installments";

    @Test
    void listsReceivablesWithFiltersAndForwardsTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT
                        + "&status=OVERDUE&dueBefore=2026-02-01&limit=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(summaryJson()));

        ReceivableSummary summary = client.findReceivables(TENANT, "OVERDUE",
                LocalDate.of(2026, 2, 1), 10);

        assertThat(summary.totalAmount()).isEqualByComparingTo("5000.0000");
        assertThat(summary.overdueAmount()).isEqualByComparingTo("5000.0000");
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.items()).hasSize(2);
        Receivable overdue = summary.items().get(0);
        assertThat(overdue.installmentId()).isEqualTo(INSTALLMENT_ID);
        assertThat(overdue.planId()).isEqualTo(PLAN_ID);
        assertThat(overdue.planNumber()).isEqualTo("BP-2026-0001");
        assertThat(overdue.policyId()).isEqualTo(POLICY_ID);
        assertThat(overdue.policyNumber()).isEqualTo("POL-2026-0000001");
        assertThat(overdue.insuredPartyId()).isEqualTo(INSURED_PARTY_ID);
        assertThat(overdue.installmentNumber()).isEqualTo(1);
        assertThat(overdue.dueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(overdue.amount()).isEqualByComparingTo("6000.0000");
        assertThat(overdue.creditedAmount()).isEqualByComparingTo("1000.0000");
        assertThat(overdue.outstandingAmount()).isEqualByComparingTo("5000.0000");
        assertThat(overdue.currency()).isEqualTo("USD");
        assertThat(overdue.status()).isEqualTo("OVERDUE");
        assertThat(overdue.daysPastDue()).isEqualTo(17);
        assertThat(overdue.paidAt()).isNull();
        assertThat(overdue.paymentId()).isNull();
        assertThat(overdue.lastDunningLevel()).isEqualTo("DUNNING_1");
        assertThat(overdue.lastDunningAt()).isEqualTo(Instant.parse("2026-02-01T09:00:00Z"));

        Receivable paid = summary.items().get(1);
        assertThat(paid.installmentId()).isEqualTo(SECOND_INSTALLMENT_ID);
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.creditedAmount()).isEqualByComparingTo("0.0000");
        assertThat(paid.outstandingAmount()).isEqualByComparingTo("0.0000");
        assertThat(paid.daysPastDue()).isZero();
        assertThat(paid.paidAt()).isEqualTo(Instant.parse("2026-01-15T12:00:00Z"));
        assertThat(paid.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(paid.lastDunningLevel()).isNull();
        assertThat(paid.lastDunningAt()).isNull();
        server.verify();
    }

    @Test
    void sendsOnlyTheTenantAndLimitWhenFiltersAreAbsent() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT + "&limit=20"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(summaryJson()));

        assertThat(client.findReceivables(TENANT, null, null, 20).items()).hasSize(2);
        server.verify();
    }

    @Test
    void computesTheSummaryWhenTheExpertOmitsDerivedMembers() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT + "&limit=20"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "items": [
                                    {"installmentId": "%s", "planId": "%s",
                                     "planNumber": "BP-2026-0001", "policyId": "%s",
                                     "policyNumber": "POL-2026-0000001",
                                     "insuredPartyId": "%s", "installmentNumber": 1,
                                     "dueDate": "2020-01-15", "amount": "6000.0000",
                                     "currency": "USD", "status": "OVERDUE"},
                                    {"installmentId": "%s", "planId": "%s",
                                     "planNumber": "BP-2026-0001", "policyId": "%s",
                                     "policyNumber": "POL-2026-0000001",
                                     "insuredPartyId": "%s", "installmentNumber": 2,
                                     "dueDate": "2026-07-15", "amount": "6000.0000",
                                     "currency": "USD", "status": "PAID",
                                     "paidAt": "2026-01-15T12:00:00Z",
                                     "paymentId": "%s"}
                                  ]
                                }
                                """.formatted(INSTALLMENT_ID, PLAN_ID, POLICY_ID, INSURED_PARTY_ID,
                                SECOND_INSTALLMENT_ID, PLAN_ID, POLICY_ID, INSURED_PARTY_ID,
                                PAYMENT_ID)));

        ReceivableSummary summary = client.findReceivables(TENANT, null, null, 20);

        assertThat(summary.totalAmount()).isEqualByComparingTo("12000.0000");
        assertThat(summary.overdueAmount()).isEqualByComparingTo("6000.0000");
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.items().get(0).daysPastDue()).isPositive();
        assertThat(summary.items().get(1).daysPastDue()).isZero();
        server.verify();
    }

    @Test
    void totalsUseTheOutstandingAmountWhenTheExpertOmitsTotals() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT + "&limit=20"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "items": [
                                    {"installmentId": "%s", "planId": "%s",
                                     "planNumber": "BP-2026-0001", "policyId": "%s",
                                     "policyNumber": "POL-2026-0000001",
                                     "insuredPartyId": "%s", "installmentNumber": 1,
                                     "dueDate": "2020-01-15", "amount": "6000.0000",
                                     "creditedAmount": "1000.0000",
                                     "outstandingAmount": "5000.0000",
                                     "currency": "USD", "status": "OVERDUE"},
                                    {"installmentId": "%s", "planId": "%s",
                                     "planNumber": "BP-2026-0001", "policyId": "%s",
                                     "policyNumber": "POL-2026-0000001",
                                     "insuredPartyId": "%s", "installmentNumber": 2,
                                     "dueDate": "2099-07-15", "amount": "6000.0000",
                                     "creditedAmount": "0.0000",
                                     "outstandingAmount": "6000.0000",
                                     "currency": "USD", "status": "DUE"}
                                  ]
                                }
                                """.formatted(INSTALLMENT_ID, PLAN_ID, POLICY_ID, INSURED_PARTY_ID,
                                SECOND_INSTALLMENT_ID, PLAN_ID, POLICY_ID, INSURED_PARTY_ID)));

        ReceivableSummary summary = client.findReceivables(TENANT, null, null, 20);

        assertThat(summary.totalAmount()).isEqualByComparingTo("11000.0000");
        assertThat(summary.overdueAmount()).isEqualByComparingTo("5000.0000");
        assertThat(summary.items().get(0).creditedAmount())
                .isEqualByComparingTo("1000.0000");
        server.verify();
    }

    @Test
    void preservesAValidationProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT
                        + "&status=SUNSET&limit=20"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-installment-status",
                                  "title": "Invalid installment status",
                                  "status": 400,
                                  "detail": "status must be one of DUE, OVERDUE, PAID, WAIVED",
                                  "code": "INVALID_INSTALLMENT_STATUS",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "status", "code": "INVALID_FORMAT",
                                     "message": "is not a known installment status"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.findReceivables(TENANT, "SUNSET", null, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_INSTALLMENT_STATUS");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("status");
                        assertThat(issue.code()).isEqualTo("INVALID_FORMAT");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheNotFoundProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT + "&limit=20"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/policy-not-found",
                                  "title": "Policy not found",
                                  "status": 404,
                                  "detail": "No receivable exists for the supplied tenant scope",
                                  "code": "POLICY_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """));

        assertThatThrownBy(() -> client.findReceivables(TENANT, null, null, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("POLICY_NOT_FOUND");
                    assertThat(api.getMessage()).contains("No receivable");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(RECEIVABLES + "?tenantId=" + TENANT + "&limit=20"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findReceivables(TENANT, null, null, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    @Test
    void agesPastUnsettledInstallmentsWhenTheExpertOmitsDaysPastDue() {
        assertThat(PolicyClient.daysPastDue(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(5),
                "DUE")).isEqualTo(5);
        assertThat(PolicyClient.daysPastDue(LocalDate.now(java.time.ZoneOffset.UTC), "OVERDUE"))
                .isZero();
        assertThat(PolicyClient.daysPastDue(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(5),
                "PAID")).isZero();
        assertThat(PolicyClient.daysPastDue(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(5),
                "WAIVED")).isZero();
        assertThat(PolicyClient.daysPastDue(null, "DUE")).isZero();
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

    private static String summaryJson() {
        return """
                {
                  "items": [
                    {"installmentId": "%s", "planId": "%s", "planNumber": "BP-2026-0001",
                     "policyId": "%s", "policyNumber": "POL-2026-0000001",
                     "insuredPartyId": "%s", "installmentNumber": 1,
                     "dueDate": "2026-01-15", "amount": "6000.0000",
                     "creditedAmount": "1000.0000", "outstandingAmount": "5000.0000",
                     "currency": "USD",
                     "status": "OVERDUE", "daysPastDue": 17, "paidAt": null,
                     "paymentId": null, "lastDunningLevel": "DUNNING_1",
                     "lastDunningAt": "2026-02-01T09:00:00Z"},
                    {"installmentId": "%s", "planId": "%s", "planNumber": "BP-2026-0001",
                     "policyId": "%s", "policyNumber": "POL-2026-0000001",
                     "insuredPartyId": "%s", "installmentNumber": 2,
                     "dueDate": "2026-07-15", "amount": "6000.0000",
                     "creditedAmount": "0.0000", "outstandingAmount": "0.0000",
                     "currency": "USD",
                     "status": "PAID", "daysPastDue": 0,
                     "paidAt": "2026-01-15T12:00:00Z", "paymentId": "%s"}
                  ],
                  "totalAmount": "5000.0000",
                  "overdueAmount": "5000.0000",
                  "count": 2
                }
                """.formatted(INSTALLMENT_ID, PLAN_ID, POLICY_ID, INSURED_PARTY_ID,
                SECOND_INSTALLMENT_ID, PLAN_ID, POLICY_ID, INSURED_PARTY_ID, PAYMENT_ID);
    }
}

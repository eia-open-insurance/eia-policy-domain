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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifies the expert contract behind policy credit notes: the tenant and
 * DIRECT book headers, the request and status payloads, the optional
 * cancellation link, the flat projection with its identity/column aliases, and
 * the preserved {@code 400}/{@code 404}/{@code 409} problems.
 */
class PolicyCreditNoteClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID CREDIT_NOTE_ID =
            UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final UUID SECOND_CREDIT_NOTE_ID =
            UUID.fromString("ffffffff-ffff-7fff-8fff-ffffffffffff");
    private static final UUID INSTALLMENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID CANCELLATION_ID =
            UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert";
    private static final String NOTES = BASE + "/internal/v1/policies/" + POLICY_ID
            + "/credit-notes";
    private static final String NOTE_STATUS = NOTES + "/" + CREDIT_NOTE_ID + "/status";

    @Test
    void requestsACreditNoteAndForwardsTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"amount": 1250.50, "currency": "USD", "reason": "CANCELLATION"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(creditNoteJson()));

        CreditNote note = client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("1250.50"), "USD", "CANCELLATION"));

        assertThat(note.creditNoteId()).isEqualTo(CREDIT_NOTE_ID);
        assertThat(note.noteNumber()).isEqualTo("CN-2026-0007");
        assertThat(note.policyId()).isEqualTo(POLICY_ID);
        assertThat(note.amount()).isEqualByComparingTo("1250.50");
        assertThat(note.currency()).isEqualTo("USD");
        assertThat(note.reason()).isEqualTo("CANCELLATION");
        assertThat(note.status()).isEqualTo("DRAFT");
        assertThat(note.issuedAt()).isNull();
        assertThat(note.appliedAt()).isNull();
        assertThat(note.createdAt()).isEqualTo(Instant.parse("2026-10-20T18:00:00Z"));
        assertThat(note.cancellationId()).isNull();
        assertThat(note.cancellationNumber()).isNull();
        server.verify();
    }

    @Test
    void requestsACancellationLinkedCreditNoteAndMapsTheLink() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"amount": 1250.50, "currency": "USD", "reason": "CANCELLATION",
                         "cancellationId": "%s"}
                        """.formatted(CANCELLATION_ID)))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cancellationCreditNoteJson()));

        CreditNote note = client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("1250.50"), "USD", "CANCELLATION",
                        CANCELLATION_ID));

        assertThat(note.creditNoteId()).isEqualTo(CREDIT_NOTE_ID);
        assertThat(note.status()).isEqualTo("DRAFT");
        assertThat(note.cancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(note.cancellationNumber()).isEqualTo("CNCL-2026-0003");
        server.verify();
    }

    @Test
    void doesNotForwardAnAbsentCancellationLink() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(not(containsString("cancellationId"))))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(creditNoteJson()));

        CreditNote note = client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("1250.50"), "USD", "CANCELLATION"));

        assertThat(note.cancellationId()).isNull();
        assertThat(note.cancellationNumber()).isNull();
        server.verify();
    }

    @Test
    void listsThePolicysCreditNotesNewestFirstWithTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(creditNoteHistoryJson()));

        List<CreditNote> notes = client.findCreditNotes(TENANT, POLICY_ID);

        assertThat(notes).hasSize(2);
        assertThat(notes).extracting(CreditNote::creditNoteId)
                .containsExactly(SECOND_CREDIT_NOTE_ID, CREDIT_NOTE_ID);
        assertThat(notes.get(0).noteNumber()).isEqualTo("CN-2026-0008");
        assertThat(notes.get(0).status()).isEqualTo("APPLIED");
        assertThat(notes.get(0).issuedAt()).isEqualTo(Instant.parse("2026-10-02T09:00:00Z"));
        assertThat(notes.get(0).appliedAt()).isEqualTo(Instant.parse("2026-10-05T09:00:00Z"));
        assertThat(notes.get(0).appliedInstallmentId()).isEqualTo(INSTALLMENT_ID);
        assertThat(notes.get(1).reason()).isEqualTo("CANCELLATION");
        assertThat(notes.get(1).status()).isEqualTo("DRAFT");
        server.verify();
    }

    @Test
    void returnsAnEmptyListWhenThePolicyHasNoCreditNote() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[]"));

        assertThat(client.findCreditNotes(TENANT, POLICY_ID)).isEmpty();
        server.verify();
    }

    @Test
    void advancesTheCreditNoteStatusWithTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTE_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"status": "ISSUED"}
                        """))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "noteNumber": "CN-2026-0007",
                                  "policyId": "%s",
                                  "amount": "1250.5000",
                                  "currency": "USD",
                                  "reason": "CANCELLATION",
                                  "status": "ISSUED",
                                  "issuedAt": "2026-10-21T10:00:00Z",
                                  "appliedAt": null,
                                  "createdAt": "2026-10-20T18:00:00Z"
                                }
                                """.formatted(CREDIT_NOTE_ID, POLICY_ID)));

        CreditNote note = client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID,
                new UpdateCreditNoteStatusCommand("ISSUED"));

        assertThat(note.creditNoteId()).isEqualTo(CREDIT_NOTE_ID);
        assertThat(note.status()).isEqualTo("ISSUED");
        assertThat(note.issuedAt()).isEqualTo(Instant.parse("2026-10-21T10:00:00Z"));
        assertThat(note.appliedAt()).isNull();
        server.verify();
    }

    @Test
    void appliesTheCreditNoteToTheInstallmentAndForwardsTheInstallmentId() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTE_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().string("""
                        {"status":"APPLIED","installmentId":"%s"}
                        """.formatted(INSTALLMENT_ID).strip()))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(appliedCreditNoteJson()));

        CreditNote note = client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID,
                new UpdateCreditNoteStatusCommand("APPLIED", INSTALLMENT_ID));

        assertThat(note.status()).isEqualTo("APPLIED");
        assertThat(note.appliedAt()).isEqualTo(Instant.parse("2026-10-21T11:00:00Z"));
        assertThat(note.appliedInstallmentId()).isEqualTo(INSTALLMENT_ID);
        server.verify();
    }

    @Test
    void reversesTheCreditNoteAndForwardsOnlyTheReason() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTE_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().string("""
                        {"status":"REVERSED","reason":"REFUND_ISSUED"}
                        """.strip()))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(reversedCreditNoteJson()));

        CreditNote note = client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID,
                new UpdateCreditNoteStatusCommand("REVERSED", INSTALLMENT_ID, "REFUND_ISSUED"));

        assertThat(note.status()).isEqualTo("REVERSED");
        assertThat(note.reversedAt()).isEqualTo(Instant.parse("2026-10-22T08:00:00Z"));
        assertThat(note.reversalReason()).isEqualTo("REFUND_ISSUED");
        assertThat(note.appliedAt()).isEqualTo(Instant.parse("2026-10-21T11:00:00Z"));
        assertThat(note.appliedInstallmentId()).isEqualTo(INSTALLMENT_ID);
        server.verify();
    }

    @Test
    void doesNotForwardAnInstallmentForANonAppliedTransition() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTE_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string("{\"status\":\"CANCELLED\"}"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "noteNumber": "CN-2026-0007",
                                  "policyId": "%s",
                                  "amount": "1250.5000",
                                  "currency": "USD",
                                  "reason": "CANCELLATION",
                                  "status": "CANCELLED",
                                  "issuedAt": null,
                                  "appliedAt": null,
                                  "appliedInstallmentId": null,
                                  "createdAt": "2026-10-20T18:00:00Z"
                                }
                                """.formatted(CREDIT_NOTE_ID, POLICY_ID)));

        CreditNote note = client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID,
                new UpdateCreditNoteStatusCommand("CANCELLED", INSTALLMENT_ID, "NOT_A_REASON"));

        assertThat(note.status()).isEqualTo("CANCELLED");
        assertThat(note.appliedInstallmentId()).isNull();
        server.verify();
    }

    @Test
    void acceptsTheExpertIdentityAndSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [{"credit_note_id": "%s", "note_number": "CN-2026-0009",
                                  "policy_id": "%s", "amount": "75.0000", "currency": "EUR",
                                  "reason": "CANCELLATION", "status": "DRAFT",
                                  "issued_at": null, "applied_at": null,
                                  "created_at": "2026-10-20T18:00:00Z",
                                  "cancellation_id": "%s",
                                  "cancellation_number": "CNCL-2026-0003"}]
                                """.formatted(CREDIT_NOTE_ID, POLICY_ID, CANCELLATION_ID)));

        List<CreditNote> notes = client.findCreditNotes(TENANT, POLICY_ID);

        assertThat(notes).singleElement().satisfies(note -> {
            assertThat(note.creditNoteId()).isEqualTo(CREDIT_NOTE_ID);
            assertThat(note.noteNumber()).isEqualTo("CN-2026-0009");
            assertThat(note.policyId()).isEqualTo(POLICY_ID);
            assertThat(note.amount()).isEqualByComparingTo("75.0000");
            assertThat(note.createdAt()).isEqualTo(Instant.parse("2026-10-20T18:00:00Z"));
            assertThat(note.cancellationId()).isEqualTo(CANCELLATION_ID);
            assertThat(note.cancellationNumber()).isEqualTo("CNCL-2026-0003");
        });
        server.verify();
    }

    @Test
    void acceptsTheReversalSpecColumnAliases() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [{"credit_note_id": "%s", "note_number": "CN-2026-0009",
                                  "policy_id": "%s", "amount": "75.0000", "currency": "EUR",
                                  "reason": "OVERPAYMENT", "status": "REVERSED",
                                  "issued_at": "2026-10-21T10:00:00Z",
                                  "applied_at": "2026-10-21T11:00:00Z",
                                  "applied_installment_id": "%s",
                                  "created_at": "2026-10-20T18:00:00Z",
                                  "reversed_at": "2026-10-22T08:00:00Z",
                                  "reversal_reason": "DUPLICATE_CHARGE"}]
                                """.formatted(CREDIT_NOTE_ID, POLICY_ID, INSTALLMENT_ID)));

        List<CreditNote> notes = client.findCreditNotes(TENANT, POLICY_ID);

        assertThat(notes).singleElement().satisfies(note -> {
            assertThat(note.status()).isEqualTo("REVERSED");
            assertThat(note.reversedAt()).isEqualTo(Instant.parse("2026-10-22T08:00:00Z"));
            assertThat(note.reversalReason()).isEqualTo("DUPLICATE_CHARGE");
        });
        server.verify();
    }

    @Test
    void preservesAValidationProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/currency-mismatch",
                                  "title": "Currency mismatch",
                                  "status": 400,
                                  "detail": "currency must match the policy currency USD.",
                                  "code": "CURRENCY_MISMATCH",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "currency", "code": "CURRENCY_MISMATCH",
                                     "message": "currency must match the policy currency USD."}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("10.00"), "EUR", "REFUND")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("CURRENCY_MISMATCH");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("currency");
                        assertThat(issue.code()).isEqualTo("CURRENCY_MISMATCH");
                    });
                });
        server.verify();
    }

    @Test
    void preservesAnInvalidCancellationLinkProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"cancellationId": "%s"}
                        """.formatted(CANCELLATION_ID)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-cancellation-link",
                                  "title": "Invalid cancellation link",
                                  "status": 400,
                                  "detail": "A VOID cancellation cannot produce a credit note.",
                                  "code": "INVALID_CANCELLATION_LINK",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "cancellationId", "code": "INVALID_CANCELLATION_LINK",
                                     "message": "cancellation is not refundable"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("10.00"), "USD", "CANCELLATION",
                        CANCELLATION_ID)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_CANCELLATION_LINK");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("cancellationId"));
                });
        server.verify();
    }

    @Test
    void preservesAMissingCancellationProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/cancellation-not-found",
                                  "title": "Cancellation not found",
                                  "status": 404,
                                  "detail": "Cancellation not found: %s",
                                  "code": "CANCELLATION_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(CANCELLATION_ID)));

        assertThatThrownBy(() -> client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("10.00"), "USD", "CANCELLATION",
                        CANCELLATION_ID)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("CANCELLATION_NOT_FOUND");
                    assertThat(api.getMessage()).contains(CANCELLATION_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesAConflictingCancellationLinkFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"cancellationId": "%s"}
                        """.formatted(CANCELLATION_ID)))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/credit-note-already-linked",
                                  "title": "Credit note already linked",
                                  "status": 409,
                                  "detail": "Cancellation %s already has a credit note.",
                                  "code": "CREDIT_NOTE_ALREADY_LINKED",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "meta": {"cancellationId": "%s"}
                                }
                                """.formatted(CANCELLATION_ID, CANCELLATION_ID)));

        assertThatThrownBy(() -> client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("10.00"), "USD", "CANCELLATION",
                        CANCELLATION_ID)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CREDIT_NOTE_ALREADY_LINKED");
                    assertThat(api.meta())
                            .containsEntry("cancellationId", CANCELLATION_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesThePolicyNotFoundProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
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

        assertThatThrownBy(() -> client.findCreditNotes(TENANT, POLICY_ID))
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
    void preservesTheMissingCreditNoteProblemFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTE_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/credit-note-not-found",
                                  "title": "Credit note not found",
                                  "status": 404,
                                  "detail": "Credit note not found: %s",
                                  "code": "CREDIT_NOTE_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(CREDIT_NOTE_ID)));

        assertThatThrownBy(() -> client.updateCreditNoteStatus(TENANT, POLICY_ID,
                CREDIT_NOTE_ID, new UpdateCreditNoteStatusCommand("APPROVED")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("CREDIT_NOTE_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void preservesTheInvalidTransitionConflictFromTheExpert() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTE_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-credit-note-transition",
                                  "title": "Invalid credit note transition",
                                  "status": 409,
                                  "detail": "A DRAFT credit note cannot move to APPLIED.",
                                  "code": "INVALID_CREDIT_NOTE_TRANSITION",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "meta": {"currentStatus": "DRAFT", "targetStatus": "APPLIED"}
                                }
                                """));

        assertThatThrownBy(() -> client.updateCreditNoteStatus(TENANT, POLICY_ID,
                CREDIT_NOTE_ID, new UpdateCreditNoteStatusCommand("APPLIED")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("INVALID_CREDIT_NOTE_TRANSITION");
                    assertThat(api.meta())
                            .containsEntry("currentStatus", "DRAFT")
                            .containsEntry("targetStatus", "APPLIED");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(NOTES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createCreditNote(TENANT, POLICY_ID,
                new CreateCreditNoteCommand(new BigDecimal("10.00"), "USD", "REFUND")))
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

    private static String creditNoteJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "noteNumber": "CN-2026-0007",
                  "policyId": "%s",
                  "amount": "1250.5000",
                  "currency": "USD",
                  "reason": "CANCELLATION",
                  "status": "DRAFT",
                  "issuedAt": null,
                  "appliedAt": null,
                  "createdAt": "2026-10-20T18:00:00Z"
                }
                """.formatted(CREDIT_NOTE_ID, TENANT, POLICY_ID);
    }

    private static String cancellationCreditNoteJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "noteNumber": "CN-2026-0007",
                  "policyId": "%s",
                  "amount": "1250.5000",
                  "currency": "USD",
                  "reason": "CANCELLATION",
                  "status": "DRAFT",
                  "issuedAt": null,
                  "appliedAt": null,
                  "createdAt": "2026-10-20T18:00:00Z",
                  "cancellationId": "%s",
                  "cancellationNumber": "CNCL-2026-0003"
                }
                """.formatted(CREDIT_NOTE_ID, TENANT, POLICY_ID, CANCELLATION_ID);
    }

    private static String appliedCreditNoteJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "noteNumber": "CN-2026-0007",
                  "policyId": "%s",
                  "amount": "1250.5000",
                  "currency": "USD",
                  "reason": "CANCELLATION",
                  "status": "APPLIED",
                  "issuedAt": "2026-10-21T10:00:00Z",
                  "appliedAt": "2026-10-21T11:00:00Z",
                  "appliedInstallmentId": "%s",
                  "createdAt": "2026-10-20T18:00:00Z",
                  "reversedAt": null,
                  "reversalReason": null
                }
                """.formatted(CREDIT_NOTE_ID, TENANT, POLICY_ID, INSTALLMENT_ID);
    }

    private static String reversedCreditNoteJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "noteNumber": "CN-2026-0007",
                  "policyId": "%s",
                  "amount": "1250.5000",
                  "currency": "USD",
                  "reason": "CANCELLATION",
                  "status": "REVERSED",
                  "issuedAt": "2026-10-21T10:00:00Z",
                  "appliedAt": "2026-10-21T11:00:00Z",
                  "appliedInstallmentId": "%s",
                  "createdAt": "2026-10-20T18:00:00Z",
                  "reversedAt": "2026-10-22T08:00:00Z",
                  "reversalReason": "REFUND_ISSUED"
                }
                """.formatted(CREDIT_NOTE_ID, TENANT, POLICY_ID, INSTALLMENT_ID);
    }

    private static String creditNoteHistoryJson() {
        return """
                [
                  {"id": "%s", "tenantId": "%s", "noteNumber": "CN-2026-0008",
                   "policyId": "%s", "amount": "320.0000", "currency": "USD",
                   "reason": "ENDORSEMENT", "status": "APPLIED",
                   "issuedAt": "2026-10-02T09:00:00Z",
                   "appliedAt": "2026-10-05T09:00:00Z",
                   "appliedInstallmentId": "%s",
                   "createdAt": "2026-10-01T09:00:00Z"},
                  {"id": "%s", "tenantId": "%s", "noteNumber": "CN-2026-0007",
                   "policyId": "%s", "amount": "1250.5000", "currency": "USD",
                   "reason": "CANCELLATION", "status": "DRAFT",
                   "issuedAt": null, "appliedAt": null,
                   "createdAt": "2026-10-20T18:00:00Z"}
                ]
                """.formatted(SECOND_CREDIT_NOTE_ID, TENANT, POLICY_ID, INSTALLMENT_ID,
                CREDIT_NOTE_ID, TENANT, POLICY_ID);
    }
}

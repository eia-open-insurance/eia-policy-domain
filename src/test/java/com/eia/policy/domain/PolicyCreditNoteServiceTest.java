package com.eia.policy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyCreditNoteServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID CREDIT_NOTE_ID =
            UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final UUID INSTALLMENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");

    private final PolicyClient client = mock(PolicyClient.class);
    private final PolicyCreditNoteService service = new PolicyCreditNoteService(client);

    @Test
    void delegatesTheListToTheExpertClient() {
        List<CreditNote> notes = List.of(note());
        when(client.findCreditNotes(TENANT, POLICY_ID)).thenReturn(notes);

        assertThat(service.list(TENANT, POLICY_ID)).isSameAs(notes);
        verify(client).findCreditNotes(TENANT, POLICY_ID);
    }

    @Test
    void delegatesTheCreateToTheExpertClient() {
        CreateCreditNoteCommand command =
                new CreateCreditNoteCommand(new BigDecimal("1250.50"), "USD", "CANCELLATION");
        CreditNote note = note();
        when(client.createCreditNote(TENANT, POLICY_ID, command)).thenReturn(note);

        assertThat(service.create(TENANT, POLICY_ID, command)).isSameAs(note);
        verify(client).createCreditNote(TENANT, POLICY_ID, command);
    }

    @Test
    void delegatesTheStatusChangeToTheExpertClient() {
        UpdateCreditNoteStatusCommand command = new UpdateCreditNoteStatusCommand("ISSUED");
        CreditNote note = note();
        when(client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command))
                .thenReturn(note);

        assertThat(service.updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command))
                .isSameAs(note);
        verify(client).updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command);
    }

    @Test
    void delegatesTheAppliedInstallmentToTheExpertClient() {
        UpdateCreditNoteStatusCommand command =
                new UpdateCreditNoteStatusCommand("APPLIED", INSTALLMENT_ID);
        CreditNote note = note();
        when(client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command))
                .thenReturn(note);

        assertThat(service.updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command))
                .isSameAs(note);
        verify(client).updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command);
    }

    @Test
    void delegatesTheReversalReasonToTheExpertClient() {
        UpdateCreditNoteStatusCommand command =
                new UpdateCreditNoteStatusCommand("REVERSED", null, "REFUND_ISSUED");
        CreditNote note = note();
        when(client.updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command))
                .thenReturn(note);

        assertThat(service.updateStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command))
                .isSameAs(note);
        verify(client).updateCreditNoteStatus(TENANT, POLICY_ID, CREDIT_NOTE_ID, command);
    }

    private static CreditNote note() {
        return new CreditNote(CREDIT_NOTE_ID, "CN-2026-0007", POLICY_ID,
                new BigDecimal("1250.50"), "USD", "CANCELLATION", "REQUESTED",
                null, null, Instant.parse("2026-10-20T18:00:00Z"));
    }
}

package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for policy credit notes. The expert owns the note
 * numbering, the reason and status vocabularies, the approval and transition
 * rules, the APPLIED installment selection and amount-fit rules, the REVERSED
 * credit release, and the SAP credit-memo reference; the domain keeps the
 * tenant and DIRECT book context on every call and forwards the optional
 * applied installment only for APPLIED and the optional reason only for
 * REVERSED.
 */
@Service
public class PolicyCreditNoteService {
    private final PolicyClient client;

    public PolicyCreditNoteService(PolicyClient client) {
        this.client = client;
    }

    public List<CreditNote> list(UUID tenantId, UUID policyId) {
        return client.findCreditNotes(tenantId, policyId);
    }

    public CreditNote create(UUID tenantId, UUID policyId, CreateCreditNoteCommand command) {
        return client.createCreditNote(tenantId, policyId, command);
    }

    public CreditNote updateStatus(UUID tenantId, UUID policyId, UUID creditNoteId,
                                   UpdateCreditNoteStatusCommand command) {
        return client.updateCreditNoteStatus(tenantId, policyId, creditNoteId, command);
    }
}

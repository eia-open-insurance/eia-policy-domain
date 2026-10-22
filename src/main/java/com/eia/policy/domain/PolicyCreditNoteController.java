package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public credit-note surface for a direct policy. Credit notes are a
 * DIRECT-book billing business; the domain forwards the tenant and the DIRECT
 * book to the Policy expert and preserves expert {@code 400}/{@code 404}/{@code
 * 409} problems unchanged.
 *
 * <p>{@code GET} returns the policy's notes as an array, newest first.
 * {@code POST} requests a note and returns {@code 201} with the stored
 * projection; the optional {@code cancellationId} links the note to the
 * cancellation whose return premium it credits. {@code PATCH .../{creditNoteId}/status}
 * advances the note through
 * the expert-owned lifecycle and returns the updated projection; the optional
 * {@code installmentId} in that body is forwarded only for {@code APPLIED}, and
 * the optional {@code reason} only for {@code REVERSED}.</p>
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/credit-notes")
public class PolicyCreditNoteController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final PolicyCreditNoteService service;

    public PolicyCreditNoteController(PolicyCreditNoteService service) {
        this.service = service;
    }

    @GetMapping
    public List<CreditNote> list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId, policyId);
    }

    @PostMapping
    public ResponseEntity<CreditNote> create(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @Valid @RequestBody CreateCreditNoteCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(tenantId, policyId, command));
    }

    @PatchMapping("/{creditNoteId}/status")
    public CreditNote updateStatus(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @PathVariable UUID creditNoteId,
            @Valid @RequestBody UpdateCreditNoteStatusCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return service.updateStatus(tenantId, policyId, creditNoteId, command);
    }
}

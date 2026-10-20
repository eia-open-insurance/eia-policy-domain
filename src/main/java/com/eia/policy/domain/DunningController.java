package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public dunning surface for one premium installment. Dunning is a DIRECT-book
 * collections business; the domain forwards the tenant and the DIRECT book to
 * the Policy expert and preserves expert {@code 400}/{@code 404}/{@code 409}
 * problems unchanged.
 *
 * <p>The route is not policy-scoped because the collections workbench addresses
 * an installment directly; the expert resolves the plan and policy from the
 * installment and keeps the tenant wall. {@code GET} returns the installment's
 * dunning history, newest first, and an empty list when nothing has been
 * recorded yet.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/installments/{installmentId}/dunning")
public class DunningController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final DunningService service;

    public DunningController(DunningService service) {
        this.service = service;
    }

    @GetMapping
    public List<DunningAction> list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID installmentId) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId, installmentId);
    }

    @PostMapping
    public ResponseEntity<DunningAction> record(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID installmentId,
            @Valid @RequestBody CreateDunningActionCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.record(tenantId, installmentId, command));
    }
}

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

import java.util.UUID;

/**
 * Public billing surface for a direct policy: the installment plan and the
 * payment state of one installment. Billing stays a DIRECT-book business; the
 * domain forwards the tenant and the DIRECT book to the Policy expert and
 * preserves expert {@code 400}/{@code 404}/{@code 409} problems unchanged.
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/billing")
public class PolicyBillingController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final PolicyBillingService service;

    public PolicyBillingController(PolicyBillingService service) {
        this.service = service;
    }

    @GetMapping
    public BillingPlan get(@RequestHeader(TENANT_HEADER) UUID tenantId,
                           @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
                           @PathVariable UUID policyId) {
        BookScope.requireDirectWhenPresent(book);
        return service.get(tenantId, policyId);
    }

    @PostMapping
    public ResponseEntity<BillingPlan> create(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @Valid @RequestBody CreateBillingPlanCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(tenantId, policyId, command));
    }

    @PatchMapping("/installments/{installmentId}")
    public Installment updateInstallment(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @PathVariable UUID installmentId,
            @Valid @RequestBody UpdateInstallmentCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return service.updateInstallment(tenantId, policyId, installmentId, command);
    }
}

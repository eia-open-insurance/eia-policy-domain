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
 * Public billing surface for a direct policy: the installment plan, the
 * payment state of one installment, and a positive premium adjustment
 * installment. Billing stays a DIRECT-book business; the domain forwards the
 * tenant and the DIRECT book to the Policy expert and preserves expert {@code
 * 400}/{@code 404}/{@code 409} problems unchanged.
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/billing")
public class PolicyBillingController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

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

    /**
     * Appends one adjustment installment for the positive premium remainder
     * between the policy written premium and the ACTIVE plan total. A fresh
     * command returns {@code 201} with the {@code {plan, installment,
     * adjustmentAmount, newTotalPremium}} envelope; an expert replay returns
     * {@code 200} with the replay marker. The optional {@code If-Match} and
     * {@code Idempotency-Key} headers are forwarded and echoed.
     */
    @PostMapping("/adjustment")
    public ResponseEntity<BillingAdjustmentResult> adjust(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody BillingAdjustmentCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return respond(service.adjust(tenantId, policyId, command, ifMatch, idempotencyKey),
                HttpStatus.CREATED);
    }

    private static <T> ResponseEntity<T> respond(Mutation<T> mutation, HttpStatus freshStatus) {
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(mutation.replayed() ? HttpStatus.OK : freshStatus)
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(mutation.replayed()));
        if (mutation.etag() != null) {
            builder = builder.eTag(mutation.etag());
        }
        return builder.body(mutation.body());
    }
}

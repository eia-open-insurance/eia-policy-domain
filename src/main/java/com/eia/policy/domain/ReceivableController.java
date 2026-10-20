package com.eia.policy.domain;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cross-policy premium-receivable read over the Policy expert's installment
 * schedule (SPEC 11.2.13). A receivable is an installment enriched with its
 * plan, policy, and insured-party context.
 *
 * <p>The route is separate from {@link PolicyBillingController} because it is
 * not scoped to one policy. The tenant is required; a supplied book must be
 * DIRECT and the domain always forwards DIRECT to the expert. Expert
 * {@code 400}/{@code 404} problems reach the caller unchanged.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/installments")
public class ReceivableController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final ReceivableService service;

    public ReceivableController(ReceivableService service) {
        this.service = service;
    }

    @GetMapping
    public ReceivableSummary list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false) Integer limit) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId, status, dueBefore, limit);
    }
}

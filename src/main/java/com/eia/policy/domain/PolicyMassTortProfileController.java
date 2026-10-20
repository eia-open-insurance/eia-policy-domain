package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public mass tort profile surface for a direct policy. The profile drives how
 * a long-tail claim is allocated across coverage blocks, so it stays a
 * DIRECT-book business; the domain forwards the tenant and the DIRECT book to
 * the Policy expert and preserves expert {@code 400}/{@code 404}/{@code 409}
 * problems unchanged.
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/mass-tort-profile")
public class PolicyMassTortProfileController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final PolicyMassTortProfileService service;

    public PolicyMassTortProfileController(PolicyMassTortProfileService service) {
        this.service = service;
    }

    @GetMapping
    public MassTortProfile get(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId) {
        BookScope.requireDirectWhenPresent(book);
        return service.get(tenantId, policyId);
    }

    @PutMapping
    public MassTortProfile update(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @Valid @RequestBody UpdateMassTortProfileCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return service.update(tenantId, policyId, command);
    }
}

package com.eia.policy.domain;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Common global-search endpoint for the Policy vertical. It is separate from
 * {@link PolicyController} so the existing list/get/create routes and their
 * expert proxy behavior stay unchanged. The DIRECT book is required before any
 * query executes.
 */
@RestController
@RequestMapping("/api/v1/policies/search")
public class PolicySearchController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final PolicySearchService service;

    public PolicySearchController(PolicySearchService service) {
        this.service = service;
    }

    @GetMapping
    public SearchResponse search(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                 @RequestHeader(value = BookScope.BOOK_HEADER, required = false)
                                 String book,
                                 @RequestParam(defaultValue = "") String q,
                                 @RequestParam(required = false) Integer limit) {
        BookScope.requireDirect(book);
        return service.search(tenantId, q, limit);
    }
}

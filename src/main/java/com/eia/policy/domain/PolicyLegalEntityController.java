package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public issuing-legal-entity assignment for a direct policy. The expert
 * applies the tenant, entity-status, and capacity rules. The domain forwards
 * the tenant and the DIRECT book context, echoes the expert {@code ETag}, and
 * preserves expert {@code 400}/{@code 404}/{@code 409} problems unchanged.
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/legal-entity")
public class PolicyLegalEntityController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final PolicyLegalEntityService service;

    public PolicyLegalEntityController(PolicyLegalEntityService service) {
        this.service = service;
    }

    @PatchMapping
    public ResponseEntity<PolicyDto> assign(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @Valid @RequestBody UpdatePolicyLegalEntityCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<PolicyDto> result = service.assign(tenantId, policyId, command.legalEntityId());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.etag() != null && !result.etag().isBlank()) {
            response.eTag(result.etag());
        }
        return response.body(result.body());
    }
}

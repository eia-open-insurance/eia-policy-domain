package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public premium-audit surface for a direct policy. The domain validates the
 * tenant, book, {@code If-Match}, and idempotency headers, forwards the command
 * to the Policy expert, and returns the updated policy DTO. When the expert
 * also supplies an audit row, the response is the
 * {@code {policy, premiumAudit}} envelope instead. Expert {@code 400}/{@code
 * 404}/{@code 409} problems are preserved unchanged, and a replayed command
 * returns {@code 200} instead of {@code 201}.
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/premium-audit")
public class PolicyPremiumAuditController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final PolicyPremiumAuditService service;

    public PolicyPremiumAuditController(PolicyPremiumAuditService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Object> apply(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ApplyPremiumAuditCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<PremiumAuditResult> result = service.apply(tenantId, policyId, command, ifMatch,
                idempotencyKey);
        PolicyDto policy = result.body() == null ? null : result.body().policy();
        Object body = policy == null || result.body().premiumAudit() != null
                ? result.body() : policy;
        String etag = result.etag();
        if ((etag == null || etag.isBlank()) && policy != null) {
            etag = EntityTags.format(policy.version());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()));
        if (etag != null && !etag.isBlank()) {
            response = response.eTag(etag);
        }
        return response.body(body);
    }
}

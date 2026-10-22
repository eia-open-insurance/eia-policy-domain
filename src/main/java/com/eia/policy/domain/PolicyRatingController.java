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

import java.util.UUID;

/**
 * Public rating surface for a direct policy. Rating is a DIRECT-book business:
 * the domain forwards {@code X-EIA-Tenant}, {@code X-EIA-Book: DIRECT}, the
 * optional {@code If-Match} tag, and the optional {@code Idempotency-Key} to
 * the Policy expert, echoes the expert {@code ETag}, and preserves expert
 * {@code 400}/{@code 404}/{@code 409} problems unchanged.
 *
 * <p>{@code POST} records one immutable worksheet and returns {@code 201}; when
 * the command applies the rating, the response also carries the refreshed
 * policy and its new ETag. {@code GET} returns the newest worksheet for the
 * policy.</p>
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}/rating")
public class PolicyRatingController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final PolicyRatingService service;

    public PolicyRatingController(PolicyRatingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PolicyRatingResult> rate(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RatePolicyCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<PolicyRatingResult> result = service.rate(tenantId, policyId, command, ifMatch,
                idempotencyKey);
        PolicyDto policy = result.body() == null ? null : result.body().policy();
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
        return response.body(result.body());
    }

    @GetMapping
    public RatingWorksheetDto latest(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId) {
        BookScope.requireDirectWhenPresent(book);
        return service.latest(tenantId, policyId);
    }
}

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

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Public underwriting-submission surface for the DIRECT book (SPEC 11.2.14).
 * The Policy expert owns the submission number, the applicant/product rules,
 * the clearance/referral/authority gates, the status transitions, and the
 * quote/bind workflow. The domain forwards the tenant and DIRECT book context,
 * the optional {@code Idempotency-Key} on mutations, and the optional
 * {@code If-Match} on status; expert {@code 400}/{@code 404}/{@code 409}
 * problems are preserved unchanged.
 *
 * <p>{@code GET} returns the tenant's submissions as an array. {@code POST}
 * creates one submission and returns {@code 201} (or {@code 200} for an expert
 * idempotent replay) with the stored projection. {@code PATCH .../{submissionId}/status}
 * advances the submission through the expert-owned lifecycle and returns the
 * updated projection.</p>
 */
@RestController
@RequestMapping("/api/v1/policies/submissions")
public class SubmissionController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @GetMapping
    public List<SubmissionDto> list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId);
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<SubmissionDto> get(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID submissionId) {
        BookScope.requireDirectWhenPresent(book);
        SubmissionDto submission = service.get(tenantId, submissionId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        String etag = etag(submission);
        if (etag != null) {
            response = response.eTag(etag);
        }
        return response.body(submission);
    }

    @PostMapping
    public ResponseEntity<SubmissionDto> create(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateSubmissionCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<SubmissionDto> result = service.create(tenantId, command, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()));
        String etag = etag(result);
        if (etag != null) {
            response = response.eTag(etag);
        }
        if (result.body() != null && result.body().submissionId() != null) {
            response = response.location(URI.create(
                    "/api/v1/policies/submissions/" + result.body().submissionId()));
        }
        return response.body(result.body());
    }

    @PatchMapping("/{submissionId}/status")
    public ResponseEntity<SubmissionDto> updateStatus(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID submissionId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody UpdateSubmissionStatusCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<SubmissionDto> result = service.updateStatus(tenantId, submissionId, command,
                ifMatch, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()));
        String etag = etag(result);
        if (etag != null) {
            response = response.eTag(etag);
        }
        return response.body(result.body());
    }

    /**
     * The strong tag echoes the expert first. When an older expert returns no
     * {@code ETag}, the aggregate {@code version} is the same representation
     * marker used by the rest of the policy API.
     */
    private static String etag(Mutation<SubmissionDto> result) {
        if (result.etag() != null && !result.etag().isBlank()) {
            return result.etag();
        }
        return etag(result.body());
    }

    private static String etag(SubmissionDto submission) {
        return submission == null || submission.version() == null
                ? null : EntityTags.format(submission.version());
    }
}

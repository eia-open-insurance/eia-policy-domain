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
 * the status transitions, the structured referral, and the quote/bind workflow.
 * The domain forwards the tenant and DIRECT book context, the optional
 * {@code Idempotency-Key} on mutations, and the optional {@code If-Match} on
 * status and referral mutations; expert {@code 400}/{@code 404}/{@code 409}
 * problems are preserved unchanged.
 *
 * <p>{@code GET} returns the tenant's submissions as an array. {@code POST}
 * creates one submission and returns {@code 201} (or {@code 200} for an expert
 * idempotent replay) with the stored projection. {@code PATCH .../{submissionId}/status}
 * advances the submission through the expert-owned lifecycle and returns the
 * updated projection. {@code GET .../{submissionId}/referrals} returns the
 * submission's referral history, newest first. {@code POST
 * .../{submissionId}/referral} opens or replaces the structured referral, and
 * {@code POST .../{submissionId}/referral/decision} decides it; both return the
 * updated projection. {@code POST .../{submissionId}/convert} turns one
 * submission into a policy and returns the {@code {submission, policy}}
 * envelope, {@code 201} for a fresh conversion (or {@code 200} for an expert
 * idempotent replay).</p>
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

    /**
     * Returns the submission's referral history, newest first, as the expert
     * publishes it. Each round carries the reason, authority, assignee, notes,
     * decision, decider, and decision instant; a pending round leaves the
     * decision fields null. The expert resolves the parent submission first, so
     * an unknown or another tenant's id is {@code 404 SUBMISSION_NOT_FOUND}.
     * The tenant and DIRECT book are forwarded and a supplied non-DIRECT book
     * is {@code 404 BOOK_SCOPE_VIOLATION}; expert {@code 400}/{@code 404}
     * problems are preserved unchanged.
     */
    @GetMapping("/{submissionId}/referrals")
    public List<ReferralDto> listReferrals(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID submissionId) {
        BookScope.requireDirectWhenPresent(book);
        return service.listReferrals(tenantId, submissionId);
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
     * Opens or replaces the structured referral on one submission. The body
     * carries the reason, required authority, assignee, and optional notes; the
     * expert owns the referrable state, moves the submission to
     * {@code REFERRED}, and starts the decision at {@code PENDING}. The tenant,
     * DIRECT book, optional {@code If-Match}, and optional
     * {@code Idempotency-Key} are forwarded, and expert {@code 400}/{@code 404}/
     * {@code 409} problems are preserved unchanged.
     */
    @PostMapping("/{submissionId}/referral")
    public ResponseEntity<SubmissionDto> refer(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID submissionId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ReferSubmissionCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<SubmissionDto> result = service.refer(tenantId, submissionId, command,
                ifMatch, idempotencyKey);
        return submissionMutationResponse(result);
    }

    /**
     * Decides the submission's pending referral. The body carries the
     * {@code APPROVED}/{@code DECLINED} decision, the decider, and optional
     * notes; the expert owns the decision vocabulary and the pending rule.
     * {@code APPROVED} keeps the submission {@code REFERRED}, while
     * {@code DECLINED} also moves it to {@code DECLINED}. The tenant, DIRECT
     * book, optional {@code If-Match}, and optional {@code Idempotency-Key} are
     * forwarded, and expert {@code 400}/{@code 404}/{@code 409} problems are
     * preserved unchanged.
     */
    @PostMapping("/{submissionId}/referral/decision")
    public ResponseEntity<SubmissionDto> decideReferral(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID submissionId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DecideReferralCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<SubmissionDto> result = service.decideReferral(tenantId, submissionId, command,
                ifMatch, idempotencyKey);
        return submissionMutationResponse(result);
    }

    /**
     * Both referral commands are {@code 200} mutations on an existing
     * submission. The strong tag echoes the expert first; when an older expert
     * returns no {@code ETag}, the aggregate {@code version} is the
     * representation marker used by the rest of the submission API.
     */
    private static ResponseEntity<SubmissionDto> submissionMutationResponse(
            Mutation<SubmissionDto> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()));
        String etag = etag(result);
        if (etag != null) {
            response = response.eTag(etag);
        }
        return response.body(result.body());
    }

    /**
     * Converts one submission into a policy. The premium and currency are
     * required, the expiration date is optional, and the expert owns whether
     * the submission is convertible. The tenant, DIRECT book, and optional
     * {@code Idempotency-Key} are forwarded; expert {@code 400}/{@code 404}/
     * {@code 409} problems are preserved unchanged.
     */
    @PostMapping("/{submissionId}/convert")
    public ResponseEntity<SubmissionConversionResult> convert(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID submissionId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ConvertSubmissionCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<SubmissionConversionResult> result = service.convert(tenantId, submissionId,
                command, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()));
        String etag = conversionEtag(result);
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

    /**
     * The conversion envelope is not itself versioned. The strong tag echoes
     * the expert first; when an older expert returns no {@code ETag}, the
     * created policy's version is the representation marker clients refresh.
     */
    private static String conversionEtag(Mutation<SubmissionConversionResult> result) {
        if (result.etag() != null && !result.etag().isBlank()) {
            return result.etag();
        }
        if (result.body() == null || result.body().policy() == null
                || result.body().policy().id() == null) {
            return null;
        }
        return EntityTags.format(result.body().policy().version());
    }
}

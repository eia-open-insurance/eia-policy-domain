package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for underwriting submissions (SPEC 11.2.14). The expert
 * owns the submission number, the applicant/product rules, the status
 * transitions, and the quote/bind workflow; the domain normalizes the
 * idempotency key and forwards the tenant and DIRECT book context.
 */
@Service
public class SubmissionService {
    private final PolicyClient client;

    public SubmissionService(PolicyClient client) {
        this.client = client;
    }

    public List<SubmissionDto> list(UUID tenantId) {
        return client.findSubmissions(tenantId);
    }

    public SubmissionDto get(UUID tenantId, UUID submissionId) {
        return client.findSubmission(tenantId, submissionId);
    }

    /**
     * Creates a submission. A fresh expert command is {@code 201}; a repeated
     * {@code Idempotency-Key} replayed by the expert is {@code 200} with the
     * {@code Idempotent-Replayed} marker.
     */
    public Mutation<SubmissionDto> create(UUID tenantId, CreateSubmissionCommand command,
                                          String idempotencyKey) {
        return client.createSubmission(tenantId, command,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    /**
     * Applies one expert-owned status transition. The optional {@code If-Match}
     * and {@code Idempotency-Key} headers are forwarded unchanged; a stale tag
     * is whatever the expert reports, normally {@code 409}.
     */
    public Mutation<SubmissionDto> updateStatus(UUID tenantId, UUID submissionId,
                                                UpdateSubmissionStatusCommand command,
                                                String ifMatch, String idempotencyKey) {
        return client.updateSubmissionStatus(tenantId, submissionId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    /**
     * Opens or replaces the structured referral on a submission. The expert
     * owns whether the submission is referrable, moves it to {@code REFERRED},
     * and starts a pending decision; the domain normalizes the optional
     * idempotency key and forwards the optional concurrency tag.
     */
    public Mutation<SubmissionDto> refer(UUID tenantId, UUID submissionId,
                                         ReferSubmissionCommand command) {
        return refer(tenantId, submissionId, command, null, null);
    }

    public Mutation<SubmissionDto> refer(UUID tenantId, UUID submissionId,
                                         ReferSubmissionCommand command, String ifMatch,
                                         String idempotencyKey) {
        return client.referSubmission(tenantId, submissionId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    /**
     * Records the decision on a pending referral. The expert owns the
     * approved/declined vocabulary and the pending rule; the domain normalizes
     * the optional idempotency key and forwards the optional concurrency tag.
     */
    public Mutation<SubmissionDto> decideReferral(UUID tenantId, UUID submissionId,
                                                  DecideReferralCommand command) {
        return decideReferral(tenantId, submissionId, command, null, null);
    }

    public Mutation<SubmissionDto> decideReferral(UUID tenantId, UUID submissionId,
                                                  DecideReferralCommand command, String ifMatch,
                                                  String idempotencyKey) {
        return client.decideSubmissionReferral(tenantId, submissionId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    /**
     * Converts a submission into a policy. The expert owns the conversion
     * eligibility and the submission-to-policy linkage; the domain normalizes
     * the optional idempotency key and forwards the tenant and DIRECT book
     * context. A fresh conversion is {@code 201}; an expert replay is
     * {@code 200} with the replay marker.
     */
    public Mutation<SubmissionConversionResult> convert(UUID tenantId, UUID submissionId,
                                                        ConvertSubmissionCommand command,
                                                        String idempotencyKey) {
        return client.convertSubmission(tenantId, submissionId, command,
                IdempotencyKeys.normalize(idempotencyKey));
    }
}

package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for underwriting submissions (SPEC 11.2.14). The expert
 * owns the submission number, the applicant/product rules, the status
 * transitions, clearance, referral, quote, and bind; the domain normalizes the
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
}

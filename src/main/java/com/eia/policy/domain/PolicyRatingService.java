package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for policy rating. The expert owns the rating methods,
 * the lifecycle gate, the premium computation, the reproducible hash, the
 * worksheet persistence, and the applied-policy version; the domain validates
 * the optional {@code If-Match} tag and normalizes the idempotency key before
 * forwarding the command and the tenant/DIRECT book context.
 */
@Service
public class PolicyRatingService {
    private final PolicyClient client;

    public PolicyRatingService(PolicyClient client) {
        this.client = client;
    }

    /**
     * Rates a policy and, when the command applies the result, updates the
     * policy premium. A fresh worksheet is {@code 201}; an expert replay is
     * surfaced as {@code 200} with the replay marker.
     */
    public Mutation<PolicyRatingResult> rate(UUID tenantId, UUID policyId,
                                            RatePolicyCommand command, String ifMatch,
                                            String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.ratePolicy(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    /** Returns the newest rating worksheet for the policy. */
    public RatingWorksheetDto latest(UUID tenantId, UUID policyId) {
        return client.findLatestRating(tenantId, policyId);
    }
}

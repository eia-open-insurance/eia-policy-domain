package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for applying a premium audit. The expert owns the audit
 * eligibility, the new policy version, the premium delta, the approval gate,
 * and the posting event; the domain validates the optional {@code If-Match}
 * tag and normalizes the idempotency key before forwarding the command.
 */
@Service
public class PolicyPremiumAuditService {
    private final PolicyClient client;

    public PolicyPremiumAuditService(PolicyClient client) {
        this.client = client;
    }

    public Mutation<PremiumAuditResult> apply(UUID tenantId, UUID policyId,
                                              ApplyPremiumAuditCommand command,
                                              String ifMatch, String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.applyPremiumAudit(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }
}

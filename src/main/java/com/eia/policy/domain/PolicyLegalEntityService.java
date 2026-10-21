package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for assigning a policy's issuing legal entity. The
 * expert owns policy and legal entity tenancy, the entity status gate, and the
 * aggregate version; the domain keeps the tenant and DIRECT book context on
 * every call.
 */
@Service
public class PolicyLegalEntityService {
    private final PolicyClient client;

    public PolicyLegalEntityService(PolicyClient client) {
        this.client = client;
    }

    public Mutation<PolicyDto> assign(UUID tenantId, UUID policyId, UUID legalEntityId) {
        return client.updatePolicyLegalEntity(tenantId, policyId, legalEntityId);
    }
}

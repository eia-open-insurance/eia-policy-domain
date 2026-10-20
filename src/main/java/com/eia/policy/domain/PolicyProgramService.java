package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for assigning a policy to a program. The expert owns
 * program membership, lead-policy, and tenant-wall rules; the domain keeps the
 * tenant and DIRECT book context on every call.
 */
@Service
public class PolicyProgramService {
    private final PolicyClient client;

    public PolicyProgramService(PolicyClient client) {
        this.client = client;
    }

    public Mutation<PolicyDto> assign(UUID tenantId, UUID policyId, UUID programId) {
        return client.updatePolicyProgram(tenantId, policyId, programId);
    }
}

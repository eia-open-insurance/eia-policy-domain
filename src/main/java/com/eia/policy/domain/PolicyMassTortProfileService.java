package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for the policy mass tort profile. The expert owns the
 * tort catalog, the trigger/allocation rules, and the profile history; the
 * domain keeps the tenant and DIRECT book context on every call.
 */
@Service
public class PolicyMassTortProfileService {
    private final PolicyClient client;

    public PolicyMassTortProfileService(PolicyClient client) {
        this.client = client;
    }

    public MassTortProfile get(UUID tenantId, UUID policyId) {
        return client.findMassTortProfile(tenantId, policyId);
    }

    public MassTortProfile update(UUID tenantId, UUID policyId,
                                  UpdateMassTortProfileCommand command) {
        return client.updateMassTortProfile(tenantId, policyId, command);
    }
}

package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for policy billing. Business billing rules stay in the
 * expert; the domain keeps the tenant and DIRECT book context on every call.
 */
@Service
public class PolicyBillingService {
    private final PolicyClient client;

    public PolicyBillingService(PolicyClient client) {
        this.client = client;
    }

    public BillingPlan get(UUID tenantId, UUID policyId) {
        return client.findBillingPlan(tenantId, policyId);
    }

    public BillingPlan create(UUID tenantId, UUID policyId, CreateBillingPlanCommand command) {
        return client.createBillingPlan(tenantId, policyId, command);
    }

    public Installment updateInstallment(UUID tenantId, UUID policyId, UUID installmentId,
                                         UpdateInstallmentCommand command) {
        return client.updateInstallment(tenantId, policyId, installmentId, command);
    }
}

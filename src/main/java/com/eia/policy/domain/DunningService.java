package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for installment dunning. The expert owns the dunning
 * rules, the level/channel vocabulary, and the audit history; the domain keeps
 * the tenant and DIRECT book context on every call.
 */
@Service
public class DunningService {
    private final PolicyClient client;

    public DunningService(PolicyClient client) {
        this.client = client;
    }

    public List<DunningAction> list(UUID tenantId, UUID installmentId) {
        return client.findDunningActions(tenantId, installmentId);
    }

    public DunningAction record(UUID tenantId, UUID installmentId,
                                CreateDunningActionCommand command) {
        return client.recordDunningAction(tenantId, installmentId, command);
    }
}

package com.eia.policy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of the mass tort profile attached to a direct policy
 * (SPEC 11.2.11). The profile records the tort and trigger theory, the
 * allocation method, and the coverage-block period that Direct Claims uses to
 * allocate a long-tail claim across policy years. The expert owns the business
 * rules; the domain proxies the projection unchanged.
 */
public record MassTortProfile(
        UUID profileId,
        UUID policyId,
        String tortType,
        String triggerType,
        String allocationMethod,
        String allocationNotes,
        LocalDate coverageBlockStart,
        LocalDate coverageBlockEnd,
        String status,
        Instant updatedAt
) {
}

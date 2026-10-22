package com.eia.policy.domain;

/**
 * Public envelope for a premium audit when the expert returns the updated
 * policy together with the audit row. A bare expert policy response is
 * published as the {@link PolicyDto} directly, so {@code premiumAudit} is
 * {@code null} only for an envelope without an audit member.
 */
public record PremiumAuditResult(PolicyDto policy, PremiumAuditDto premiumAudit) {
}

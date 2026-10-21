package com.eia.policy.domain;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Assigns the issuing legal entity to a policy. SPEC LE-01 requires every
 * policy to reference one issuing legal entity, so the assignment cannot be
 * cleared; the expert owns the tenant, status, and capacity rules.
 */
public record UpdatePolicyLegalEntityCommand(@NotNull UUID legalEntityId) {
}

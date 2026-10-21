package com.eia.policy.domain;

import java.util.UUID;

/**
 * Assigns the issuing legal entity to a policy. A null id clears the
 * association; the expert owns the tenant, status, and capacity rules.
 */
public record UpdatePolicyLegalEntityCommand(UUID legalEntityId) {
}

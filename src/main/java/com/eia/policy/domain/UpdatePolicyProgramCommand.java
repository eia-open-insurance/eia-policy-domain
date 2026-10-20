package com.eia.policy.domain;

import java.util.UUID;

/**
 * Assigns a policy to a program, or clears the assignment when
 * {@code programId} is null. The expert owns the program membership,
 * lead-policy, and tenant-wall rules.
 */
public record UpdatePolicyProgramCommand(UUID programId) {
}

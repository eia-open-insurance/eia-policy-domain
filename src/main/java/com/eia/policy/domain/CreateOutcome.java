package com.eia.policy.domain;

/**
 * Result of a create call. {@code replayed} is true when an existing policy was
 * returned for a repeated {@code Idempotency-Key} instead of creating a new one.
 */
public record CreateOutcome(PolicyDto policy, boolean replayed) {
}

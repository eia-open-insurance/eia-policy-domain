package com.eia.policy.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded dunning action for a premium installment (SPEC 11.2.13
 * collections and dunning). The expert owns the dunning level/channel
 * vocabulary, the allowed transitions, and the audit history; the domain
 * publishes the flat projection the collections workbench renders.
 *
 * <p>{@code status} is the expert's action state ({@code SENT} for a delivered
 * action, {@code CANCELLED} only for superseded history) and {@code sentAt} is
 * the instant the action was recorded. {@code notes} is absent when the caller
 * did not supply one.</p>
 */
public record DunningAction(
        UUID dunningId,
        UUID installmentId,
        UUID planId,
        UUID policyId,
        String level,
        String channel,
        String notes,
        String status,
        Instant sentAt
) {
}

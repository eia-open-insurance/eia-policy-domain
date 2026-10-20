package com.eia.policy.domain;

import java.util.UUID;

/**
 * One row of the common global-search result shape. Policy emits {@code type}
 * {@code POLICY}; the shell merges this with the other verticals.
 */
public record SearchItem(
        UUID id,
        String type,
        String label,
        String secondary,
        String status,
        String href
) {}

package com.eia.policy.domain;

import java.util.UUID;

/**
 * Projection read by the Policy search query; mapping to the public envelope
 * happens in {@link PolicySearchService}.
 */
record PolicySearchRow(
        UUID id,
        String policyNumber,
        String productCode,
        String status
) {}

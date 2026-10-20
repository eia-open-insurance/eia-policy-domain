package com.eia.policy.domain;

import java.util.List;

/**
 * Common global-search response shape shared by every vertical.
 */
public record SearchResponse(
        String query,
        int limit,
        List<SearchItem> items
) {}

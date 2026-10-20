package com.eia.policy.domain;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Normalized search input: the term is trimmed and the limit is defaulted to
 * 10 and capped at 25 so every vertical returns the same envelope.
 */
record SearchQuery(String term, int limit) {
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 25;

    static SearchQuery of(String q, Integer limit) {
        String term = q == null ? "" : q.trim();
        int requested = limit == null ? DEFAULT_LIMIT : limit;
        if (requested < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Search query is invalid.",
                    List.of(new ApiException.FieldIssue(
                            "limit", "OUT_OF_RANGE", "limit must be at least 1")));
        }
        return new SearchQuery(term, Math.min(requested, MAX_LIMIT));
    }
}

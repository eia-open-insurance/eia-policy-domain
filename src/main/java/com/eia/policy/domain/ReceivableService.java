package com.eia.policy.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Public facade for the cross-policy premium-receivable read. The domain owns
 * only the request shape: it normalizes the optional status filter and the
 * limit, then forwards the tenant and DIRECT book context to the Policy expert,
 * which owns billing and receivable rules.
 */
@Service
public class ReceivableService {
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final PolicyClient client;

    public ReceivableService(PolicyClient client) {
        this.client = client;
    }

    public ReceivableSummary list(UUID tenantId, String status, LocalDate dueBefore, Integer limit) {
        return client.findReceivables(tenantId, normalizeStatus(status), dueBefore,
                normalizeLimit(limit));
    }

    /**
     * Installment statuses are canonical upper-case tokens. A blank filter is
     * absent; any other value is forwarded upper-cased so a case-only mismatch
     * does not silently return an empty page. Unknown values stay the expert's
     * 400 to raise.
     */
    static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    static int normalizeLimit(Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "The limit parameter is invalid.",
                    List.of(new ApiException.FieldIssue("limit", "OUT_OF_RANGE",
                            "limit must be between 1 and " + MAX_LIMIT)));
        }
        return effective;
    }
}

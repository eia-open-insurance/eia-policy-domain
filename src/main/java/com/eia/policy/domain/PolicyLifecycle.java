package com.eia.policy.domain;

import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Policy status lifecycle from SPEC.md section 11.2.6.
 */
public final class PolicyLifecycle {
    public static final Set<String> STATUSES = Set.of(
            "DRAFT", "QUOTED", "BOUND", "ISSUED", "IN_FORCE", "ENDORSED",
            "RENEWED", "CANCELLED", "EXPIRED", "NON_RENEWED", "REINSTATED", "VOID"
    );

    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry("DRAFT", Set.of("QUOTED", "CANCELLED")),
            Map.entry("QUOTED", Set.of("BOUND", "EXPIRED")),
            Map.entry("BOUND", Set.of("ISSUED")),
            Map.entry("ISSUED", Set.of("IN_FORCE")),
            Map.entry("IN_FORCE", Set.of("ENDORSED", "CANCELLED", "EXPIRED", "NON_RENEWED", "RENEWED", "VOID")),
            Map.entry("ENDORSED", Set.of("IN_FORCE")),
            Map.entry("CANCELLED", Set.of("REINSTATED", "VOID")),
            Map.entry("REINSTATED", Set.of("IN_FORCE")),
            Map.entry("EXPIRED", Set.of("RENEWED", "REINSTATED")),
            Map.entry("RENEWED", Set.of()),
            Map.entry("NON_RENEWED", Set.of()),
            Map.entry("VOID", Set.of())
    );

    private PolicyLifecycle() {
    }

    public static String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "status is required");
        }
        String status = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
                    "Unknown policy status: " + rawStatus);
        }
        return status;
    }

    public static String validateTransition(String fromStatus, String toStatus) {
        String from = normalizeStatus(fromStatus);
        String to = normalizeStatus(toStatus);
        if (from.equals(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Policy is already in status " + from);
        }
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Cannot transition policy from " + from + " to " + to);
        }
        return to;
    }
}

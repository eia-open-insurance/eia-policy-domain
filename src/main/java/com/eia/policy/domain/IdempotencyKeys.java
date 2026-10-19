package com.eia.policy.domain;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Validates and normalizes the optional {@code Idempotency-Key} request header.
 * The key is optional for compatibility; when supplied it scopes a create to
 * the tenant that sent it.
 */
final class IdempotencyKeys {
    static final int MAX_LENGTH = 255;

    private IdempotencyKeys() {
    }

    static String normalize(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String key = rawKey.trim();
        if (key.isEmpty()) {
            return null;
        }
        if (key.length() > MAX_LENGTH) {
            String message = "Idempotency-Key must be at most " + MAX_LENGTH + " characters.";
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", message,
                    List.of(new ApiException.FieldIssue(
                            "Idempotency-Key", "INVALID_IDEMPOTENCY_KEY", message)));
        }
        return key;
    }
}

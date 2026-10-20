package com.eia.policy.domain;

import org.springframework.http.HttpStatus;

/**
 * Policy is a DIRECT-book vertical (SPEC 3.6, 7.18.5). Search requires an
 * explicit book header and hides the endpoint from any other book; the wall is
 * enforced before any SQL runs.
 */
final class BookScope {
    static final String BOOK_HEADER = "X-EIA-Book";
    static final String DIRECT = "DIRECT";

    private BookScope() {
    }

    static void requireDirect(String book) {
        if (book == null || book.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOK_REQUIRED",
                    "X-EIA-Book header is required");
        }
        if (!DIRECT.equalsIgnoreCase(book.trim())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_SCOPE_VIOLATION",
                    "Policy is a DIRECT-only vertical; X-EIA-Book must be DIRECT");
        }
    }

    /**
     * Billing keeps the DIRECT book context but accepts an absent header for
     * compatibility with the other policy proxy routes. A supplied book that
     * is not DIRECT is hidden exactly like a search from another book.
     */
    static void requireDirectWhenPresent(String book) {
        if (book != null && !book.isBlank()) {
            requireDirect(book);
        }
    }
}

package com.eia.policy.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a product in the DIRECT book catalog (SPEC 11.2.14).
 *
 * <p>A product is the sellable taxonomy record that policies and underwriting
 * submissions reference by {@code productCode}. The Policy expert owns the
 * catalog, the product-line vocabulary, tenant-scoped code uniqueness, the
 * ACTIVE/RETIRED lifecycle, and the aggregate version; the domain publishes the
 * flat projection without the tenant column.</p>
 *
 * <p>{@code version} is the aggregate sequence and backs the response
 * {@code ETag}. A product starts at version zero and each accepted status change
 * advances it by one; replaying the current status leaves the version
 * unchanged.</p>
 */
public record ProductDto(
        UUID productId,
        String productCode,
        String productName,
        String productLine,
        String jurisdiction,
        String status,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}

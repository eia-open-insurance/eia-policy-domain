package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates one product catalog record. The identity, status, version, and
 * timestamps are server-owned: a new product starts {@code ACTIVE}. The expert
 * owns the product-line vocabulary, the two-letter jurisdiction rule, the
 * upper-case code/line normalization, the name and code caps, and tenant-scoped
 * code uniqueness; the domain validates only the request shape.
 *
 * <p>The short aliases keep the body compatible with clients and with the
 * expert's canonical create request, which accepts {@code code}, {@code name},
 * and {@code line} as well.</p>
 */
public record CreateProductCommand(
        @NotBlank @Size(max = 32) @JsonAlias("code") String productCode,
        @NotBlank @Size(max = 200) @JsonAlias("name") String productName,
        @NotBlank @JsonAlias("line") String productLine,
        @NotBlank @Size(max = 2) String jurisdiction
) {
}

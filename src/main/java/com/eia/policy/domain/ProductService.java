package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for the product catalog. The Policy expert owns the
 * product-line vocabulary, tenant-scoped code uniqueness, the ACTIVE/RETIRED
 * lifecycle and its version, and the catalog search; the domain validates the
 * request shape, forwards the tenant and DIRECT-book context, and preserves the
 * expert's {@code 400}/{@code 404}/{@code 409} problems.
 */
@Service
public class ProductService {
    private final PolicyClient client;

    public ProductService(PolicyClient client) {
        this.client = client;
    }

    /**
     * Lists or searches the tenant's products. {@code q} is a case-insensitive
     * substring of the code or name and {@code status} is the ACTIVE/RETIRED
     * filter; both are optional and combine with AND. The expert owns the
     * matching, ordering, and status vocabulary, so its 400 is preserved.
     */
    public List<ProductDto> list(UUID tenantId, String q, String status) {
        return client.findProducts(tenantId, normalizeFilter(q), normalizeFilter(status));
    }

    public Mutation<ProductDto> get(UUID tenantId, UUID productId) {
        return client.findProduct(tenantId, productId);
    }

    public Mutation<ProductDto> create(UUID tenantId, CreateProductCommand command) {
        return client.createProduct(tenantId, command);
    }

    public Mutation<ProductDto> updateStatus(UUID tenantId, UUID productId,
                                             UpdateProductStatusCommand command) {
        return client.updateProductStatus(tenantId, productId, command);
    }

    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

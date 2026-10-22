package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Public product catalog for the DIRECT book (SPEC 11.2.14). The Policy expert
 * owns the product-line vocabulary, tenant-scoped code uniqueness, the
 * ACTIVE/RETIRED lifecycle, the aggregate version, and catalog search. The
 * domain forwards the tenant and the DIRECT book context and preserves expert
 * {@code 400}/{@code 404}/{@code 409} problems unchanged.
 *
 * <p>The list accepts the optional {@code q} (case-insensitive code/name
 * substring) and {@code status} filters; {@code /search} is an alias for the
 * same read so a caller can treat list and search as separate operations
 * without a second projection.</p>
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductDto> list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId, q, status);
    }

    @GetMapping("/search")
    public List<ProductDto> search(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId, q, status);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDto> get(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID productId) {
        BookScope.requireDirectWhenPresent(book);
        return withEtag(ResponseEntity.ok(), service.get(tenantId, productId));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @Valid @RequestBody CreateProductCommand command) {
        BookScope.requireDirectWhenPresent(book);
        Mutation<ProductDto> created = service.create(tenantId, command);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (created.body() != null && created.body().productId() != null) {
            response.location(URI.create(
                    "/api/v1/products/" + created.body().productId()));
        }
        return withEtag(response, created);
    }

    @PatchMapping("/{productId}/status")
    public ResponseEntity<ProductDto> updateStatus(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductStatusCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return withEtag(ResponseEntity.ok(),
                service.updateStatus(tenantId, productId, command));
    }

    /**
     * Carries the expert ETag through unchanged, falling back to the aggregate
     * {@code version} when an older expert omits the header. The fallback keeps
     * the strong tag coherent with the returned projection.
     */
    private static ResponseEntity<ProductDto> withEtag(
            ResponseEntity.BodyBuilder builder, Mutation<ProductDto> mutation) {
        String etag = mutation.etag();
        if ((etag == null || etag.isBlank())
                && mutation.body() != null && mutation.body().version() != null) {
            etag = "\"" + mutation.body().version() + "\"";
        }
        if (etag != null && !etag.isBlank()) {
            builder.header(HttpHeaders.ETAG, etag);
        }
        return builder.body(mutation.body());
    }
}

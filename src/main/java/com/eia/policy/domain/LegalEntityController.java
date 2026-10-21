package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Public legal entity catalog for the DIRECT book. The Policy expert owns the
 * entity code, the entity-type and status vocabularies, tenant-scoped
 * uniqueness, and the regulator and license rules. The domain forwards the
 * tenant and the DIRECT book context and preserves expert
 * {@code 400}/{@code 404}/{@code 409} problems unchanged.
 */
@RestController
@RequestMapping("/api/v1/legal-entities")
public class LegalEntityController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final LegalEntityService service;

    public LegalEntityController(LegalEntityService service) {
        this.service = service;
    }

    @GetMapping
    public List<LegalEntityDto> list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId);
    }

    @GetMapping("/{legalEntityId}")
    public LegalEntityDto get(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID legalEntityId) {
        BookScope.requireDirectWhenPresent(book);
        return service.get(tenantId, legalEntityId);
    }

    @PostMapping
    public ResponseEntity<LegalEntityDto> create(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @Valid @RequestBody CreateLegalEntityCommand command) {
        BookScope.requireDirectWhenPresent(book);
        LegalEntityDto legalEntity = service.create(tenantId, command);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (legalEntity.legalEntityId() != null) {
            response.location(URI.create(
                    "/api/v1/legal-entities/" + legalEntity.legalEntityId()));
        }
        return response.body(legalEntity);
    }
}

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
 * Public program catalog for the DIRECT book. The Policy expert owns program
 * numbering, the program-type and status vocabularies, tenant-scoped
 * uniqueness, and the effective-date rules. The domain forwards the tenant and
 * the DIRECT book context and preserves expert {@code 400}/{@code 404}/{@code
 * 409} problems unchanged.
 */
@RestController
@RequestMapping("/api/v1/programs")
public class ProgramController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";

    private final ProgramService service;

    public ProgramController(ProgramService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProgramDto> list(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book) {
        BookScope.requireDirectWhenPresent(book);
        return service.list(tenantId);
    }

    @GetMapping("/{programId}")
    public ProgramDto get(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID programId) {
        BookScope.requireDirectWhenPresent(book);
        return service.get(tenantId, programId);
    }

    @PostMapping
    public ResponseEntity<ProgramDto> create(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @Valid @RequestBody CreateProgramCommand command) {
        BookScope.requireDirectWhenPresent(book);
        ProgramDto program = service.create(tenantId, command);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (program.programId() != null) {
            response.location(URI.create("/api/v1/programs/" + program.programId()));
        }
        return response.body(program);
    }
}

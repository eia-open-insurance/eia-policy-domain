package com.eia.policy.domain;

import jakarta.validation.Valid;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    public PolicyPage list(@RequestHeader(TENANT_HEADER) UUID tenantId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return service.list(tenantId, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyDto> get(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                         @PathVariable UUID id) {
        PolicyDto policy = service.get(tenantId, id);
        return ResponseEntity.ok()
                .eTag(Integer.toString(policy.version()))
                .body(policy);
    }

    @PostMapping
    public ResponseEntity<PolicyDto> create(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                            @RequestHeader(value = IDEMPOTENCY_KEY, required = false)
                                            String idempotencyKey,
                                            @Valid @RequestBody CreatePolicyCommand command) {
        CreateOutcome outcome = service.create(tenantId, command, idempotencyKey);
        HttpStatus status = outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .eTag(Integer.toString(outcome.policy().version()))
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(outcome.replayed()))
                .body(outcome.policy());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PolicyDto> updateStatus(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdatePolicyStatusCommand command) {
        PolicyDto policy = service.updateStatus(tenantId, id, command, ifMatch);
        return ResponseEntity.ok()
                .eTag(Integer.toString(policy.version()))
                .body(policy);
    }
}

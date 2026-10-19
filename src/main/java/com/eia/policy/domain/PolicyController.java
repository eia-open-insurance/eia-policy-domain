package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {
    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    public List<PolicyDto> list(@RequestHeader("X-EIA-Tenant") UUID tenantId) {
        return service.list(tenantId);
    }

    @GetMapping("/{id}")
    public PolicyDto get(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyDto create(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                            @Valid @RequestBody CreatePolicyCommand command) {
        return service.create(tenantId, command);
    }

    @PatchMapping("/{id}/status")
    public PolicyDto updateStatus(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdatePolicyStatusCommand command) {
        return service.updateStatus(tenantId, id, command);
    }
}

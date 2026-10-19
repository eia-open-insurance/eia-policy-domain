package com.eia.policy.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PolicyService {
    private final PolicyClient client;

    public PolicyService(PolicyClient client) {
        this.client = client;
    }

    public List<PolicyDto> list(UUID tenantId) {
        return client.findAll(tenantId);
    }

    public PolicyDto get(UUID tenantId, UUID id) {
        return client.findById(tenantId, id);
    }

    public PolicyDto create(UUID tenantId, CreatePolicyCommand command) {
        validateCreate(command.effectiveDate(), command.expirationDate(),
                command.premiumAmount(), command.limitAmount());
        return client.create(tenantId, command);
    }

    public PolicyDto updateStatus(UUID tenantId, UUID id, UpdatePolicyStatusCommand command) {
        PolicyDto current = client.findById(tenantId, id);
        String targetStatus = PolicyLifecycle.validateTransition(current.status(), command.status());
        int expectedVersion = command.expectedVersion() == null
                ? current.version()
                : command.expectedVersion();
        return client.updateStatus(tenantId, id, targetStatus, expectedVersion, command.reason());
    }

    private static void validateCreate(LocalDate effectiveDate, LocalDate expirationDate,
                                       BigDecimal premiumAmount, BigDecimal limitAmount) {
        List<ApiException.FieldIssue> issues = new ArrayList<>();
        if (effectiveDate != null && expirationDate != null && effectiveDate.isAfter(expirationDate)) {
            issues.add(new ApiException.FieldIssue("expirationDate",
                    "expirationDate must be on or after effectiveDate"));
        }
        if (premiumAmount != null && premiumAmount.signum() < 0) {
            issues.add(new ApiException.FieldIssue("premiumAmount", "premiumAmount must be non-negative"));
        }
        if (limitAmount != null && limitAmount.signum() < 0) {
            issues.add(new ApiException.FieldIssue("limitAmount", "limitAmount must be non-negative"));
        }
        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Policy validation failed", issues);
        }
    }
}

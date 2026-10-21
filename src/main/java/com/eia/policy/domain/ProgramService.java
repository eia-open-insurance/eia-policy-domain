package com.eia.policy.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for the program catalog. The expert owns program
 * numbering, the type/status vocabulary, tenant-scoped uniqueness, policy
 * membership, and the capacity aggregates; the domain validates the request
 * shape before delegating.
 */
@Service
public class ProgramService {
    private final PolicyClient client;

    public ProgramService(PolicyClient client) {
        this.client = client;
    }

    public List<ProgramDto> list(UUID tenantId) {
        return client.findPrograms(tenantId);
    }

    public ProgramDto get(UUID tenantId, UUID programId) {
        return client.findProgram(tenantId, programId);
    }

    public ProgramDto create(UUID tenantId, CreateProgramCommand command) {
        validateDates(command.effectiveFrom(), command.effectiveTo());
        validateCapacity(command.capacityAmount(), command.capacityCurrency());
        return client.createProgram(tenantId, command);
    }

    private static void validateDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Program validation failed",
                    List.of(new ApiException.FieldIssue("effectiveTo", "INVALID_DATE_RANGE",
                            "effectiveTo must be on or after effectiveFrom")));
        }
    }

    /**
     * A capacity limit is a single amount/currency pair. Bean validation already
     * rejects a non-positive amount and a malformed currency; this check only
     * enforces that the two optional members are supplied together.
     */
    private static void validateCapacity(BigDecimal capacityAmount, String capacityCurrency) {
        if (capacityAmount == null && capacityCurrency == null) {
            return;
        }
        List<ApiException.FieldIssue> issues = new ArrayList<>();
        if (capacityAmount == null) {
            issues.add(new ApiException.FieldIssue("capacityAmount", "CAPACITY_AMOUNT_REQUIRED",
                    "capacityAmount is required when capacityCurrency is supplied"));
        }
        if (capacityCurrency == null) {
            issues.add(new ApiException.FieldIssue("capacityCurrency", "CAPACITY_CURRENCY_REQUIRED",
                    "capacityCurrency is required when capacityAmount is supplied"));
        }
        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Program validation failed", issues);
        }
    }
}

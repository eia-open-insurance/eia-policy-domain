package com.eia.policy.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for the program catalog. The expert owns program
 * numbering, the type/status vocabulary, tenant-scoped uniqueness, and policy
 * membership; the domain validates the request shape before delegating.
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
}

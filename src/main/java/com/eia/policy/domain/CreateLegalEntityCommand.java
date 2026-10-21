package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a legal entity. The expert owns tenant-scoped code uniqueness, the
 * entity-type and status vocabularies, and the license and regulator rules, and
 * it generates the identity, status, and creation instant; the domain validates
 * only the request shape.
 */
public record CreateLegalEntityCommand(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 32) String entityType,
        @NotBlank @Size(max = 2) String jurisdiction,
        @Size(max = 120) String regulator,
        @Size(max = 80) String licenseNumber
) {
}

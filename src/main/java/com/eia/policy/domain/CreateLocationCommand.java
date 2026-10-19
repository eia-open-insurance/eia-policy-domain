package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Add a scheduled location to a policy. */
public record CreateLocationCommand(
        @NotBlank @Size(max = 32) String locationCode,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 6) String stateRegion,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 3) String country,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo
) {
}

package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Replaces the mass tort profile of a direct policy. The domain validates the
 * request shape only; the expert validates the tort catalog, the trigger and
 * allocation vocabulary, the coverage-block period, and the profile state.
 * {@code trigger} is accepted as an alias for {@code triggerType} so the
 * policy record UI can post the label it renders.
 */
public record UpdateMassTortProfileCommand(
        @NotBlank String tortType,
        @JsonAlias("trigger") @NotBlank String triggerType,
        @NotBlank String allocationMethod,
        @Size(max = 2000) String allocationNotes,
        LocalDate coverageBlockStart,
        LocalDate coverageBlockEnd,
        @NotBlank @Size(max = 32) String status
) {
}

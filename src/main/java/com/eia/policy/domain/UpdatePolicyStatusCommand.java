package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePolicyStatusCommand(
        @NotBlank String status,
        Integer expectedVersion,
        @Size(max = 200) String reason
) {}

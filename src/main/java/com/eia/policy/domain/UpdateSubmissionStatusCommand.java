package com.eia.policy.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Advances one underwriting submission through the expert-owned lifecycle
 * ({@code RECEIVED}, {@code REFERRED}, {@code QUOTED}, {@code BOUND},
 * {@code DECLINED}, {@code WITHDRAWN}). The domain validates only the request
 * shape; the expert owns the transition table and the 400/404/409 problems.
 */
public record UpdateSubmissionStatusCommand(
        @NotBlank @Size(max = 32) String status,
        @Size(max = 500) String reason
) {
}

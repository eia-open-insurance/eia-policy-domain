package com.eia.policy.domain;

/** A coverage with its limit schedule and current erosion state. */
public record CoverageViewDto(CoverageDto coverage, LimitDto limit, LimitStateDto state) {
}

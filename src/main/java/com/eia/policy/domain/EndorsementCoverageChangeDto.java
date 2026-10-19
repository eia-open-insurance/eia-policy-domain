package com.eia.policy.domain;

/** One coverage change carried by an endorsement. */
public record EndorsementCoverageChangeDto(String coverageCode, String changeType,
                                           String description) {
}

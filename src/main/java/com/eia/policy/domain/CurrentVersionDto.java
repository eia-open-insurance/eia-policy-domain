package com.eia.policy.domain;

/** The derived current version plus the snapshot hash verification result. */
public record CurrentVersionDto(PolicyVersionDto version, boolean current, boolean hashVerified) {
}

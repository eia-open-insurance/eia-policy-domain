package com.eia.policy.domain;

import java.util.List;

/** One page of tenant-scoped policies and the filtered total. */
public record PolicyPageData(List<PolicyDto> items, long total) {
}

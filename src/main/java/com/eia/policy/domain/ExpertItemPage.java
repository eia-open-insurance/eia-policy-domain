package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Wire shape of an expert sub-resource list. The domain rebuilds the links for
 * its own public base path.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpertItemPage<T>(List<T> items, int page, int size, long total) {
}

package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Wire shape of the expert list endpoint. The expert returns the same page
 * envelope; links are rebuilt for the public path by the domain service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpertPolicyPage(List<PolicyDto> items, int page, int size, long total) {
}

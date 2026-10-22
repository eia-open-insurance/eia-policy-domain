package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public response of a submission conversion: the converted submission and the
 * policy created from it. The expert owns both projections; the domain only
 * fills the request tenant scope. The converted submission carries the new
 * {@code policyId} when the expert exposes it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmissionConversionResult(SubmissionDto submission, PolicyDto policy) {
}

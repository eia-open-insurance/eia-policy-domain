package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public response of a rating command, matching the expert envelope: the
 * persisted worksheet and, only when the caller asked to apply it, the
 * refreshed policy with the new premium and aggregate version. A
 * calculation-only request omits {@code policy}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyRatingResult(RatingWorksheetDto worksheet, PolicyDto policy) {
}

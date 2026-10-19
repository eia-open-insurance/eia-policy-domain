package com.eia.policy.domain;

import org.springframework.http.ResponseEntity;

/**
 * Result of a domain mutation proxied to the expert: the body plus the strong
 * entity tag and the idempotent replay marker.
 */
public record Mutation<T>(T body, String etag, boolean replayed) {
    public static <T> Mutation<T> of(ResponseEntity<T> response) {
        String replayHeader = response.getHeaders().getFirst("Idempotent-Replayed");
        boolean replayed = replayHeader == null
                ? response.getStatusCode().value() == 200
                : Boolean.parseBoolean(replayHeader);
        return new Mutation<>(response.getBody(), response.getHeaders().getETag(), replayed);
    }
}

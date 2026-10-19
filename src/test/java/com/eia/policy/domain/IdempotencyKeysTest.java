package com.eia.policy.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeysTest {
    @Test
    void normalizesOptionalKey() {
        assertThat(IdempotencyKeys.normalize(null)).isNull();
        assertThat(IdempotencyKeys.normalize("   ")).isNull();
        assertThat(IdempotencyKeys.normalize("  key-1 ")).isEqualTo("key-1");
    }

    @Test
    void rejectsOverlongKey() {
        assertThatThrownBy(() -> IdempotencyKeys.normalize("k".repeat(256)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("INVALID_IDEMPOTENCY_KEY"));
    }
}

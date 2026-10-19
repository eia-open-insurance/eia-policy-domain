package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyLifecycleTest {
    @Test
    void acceptsTheSpecLifecycle() {
        List<String[]> transitions = List.of(
                new String[]{"DRAFT", "QUOTED"},
                new String[]{"DRAFT", "CANCELLED"},
                new String[]{"QUOTED", "BOUND"},
                new String[]{"QUOTED", "EXPIRED"},
                new String[]{"BOUND", "ISSUED"},
                new String[]{"ISSUED", "IN_FORCE"},
                new String[]{"IN_FORCE", "ENDORSED"},
                new String[]{"ENDORSED", "IN_FORCE"},
                new String[]{"IN_FORCE", "CANCELLED"},
                new String[]{"CANCELLED", "REINSTATED"},
                new String[]{"REINSTATED", "IN_FORCE"},
                new String[]{"EXPIRED", "RENEWED"},
                new String[]{"EXPIRED", "REINSTATED"},
                new String[]{"IN_FORCE", "RENEWED"},
                new String[]{"IN_FORCE", "VOID"},
                new String[]{"CANCELLED", "VOID"}
        );
        transitions.forEach(transition ->
                assertThat(PolicyLifecycle.validateTransition(transition[0], transition[1]))
                        .isEqualTo(transition[1]));
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(PolicyLifecycle.normalizeStatus("  in_force ")).isEqualTo("IN_FORCE");
        assertThat(PolicyLifecycle.validateTransition("draft", "quoted")).isEqualTo("QUOTED");
    }

    @Test
    void rejectsSkippedTransitions() {
        assertThatThrownBy(() -> PolicyLifecycle.validateTransition("DRAFT", "ISSUED"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_STATUS_TRANSITION");
                });
    }

    @Test
    void rejectsSameStatusAndTerminalStates() {
        assertThatThrownBy(() -> PolicyLifecycle.validateTransition("IN_FORCE", "IN_FORCE"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PolicyLifecycle.validateTransition("VOID", "IN_FORCE"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PolicyLifecycle.validateTransition("NON_RENEWED", "RENEWED"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> PolicyLifecycle.normalizeStatus("PENDING"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("INVALID_STATUS"));
    }
}

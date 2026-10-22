package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Service-level checks for the billing adjustment: header validation and
 * idempotency normalization happen before the expert call, and the client
 * mutation is returned unchanged.
 */
class PolicyBillingAdjustmentServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PLAN_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID INSTALLMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final BillingAdjustmentCommand COMMAND = new BillingAdjustmentCommand(
            LocalDate.of(2026, 5, 1), "exposure audit");

    private final PolicyClient client = mock(PolicyClient.class);
    private final PolicyBillingService service = new PolicyBillingService(client);

    @Test
    void delegatesTheAdjustmentAndNormalizesTheIdempotencyKey() {
        Mutation<BillingAdjustmentResult> mutation =
                new Mutation<>(result(), "\"5\"", false);
        when(client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND, "\"4\"", "adjust-key-1"))
                .thenReturn(mutation);

        assertThat(service.adjust(TENANT, POLICY_ID, COMMAND, "\"4\"", " adjust-key-1 "))
                .isSameAs(mutation);
        verify(client).adjustBillingPlan(TENANT, POLICY_ID, COMMAND, "\"4\"", "adjust-key-1");
    }

    @Test
    void forwardsAbsentConcurrencyAndIdempotencyHeaders() {
        Mutation<BillingAdjustmentResult> mutation =
                new Mutation<>(result(), null, false);
        when(client.adjustBillingPlan(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(mutation);

        assertThat(service.adjust(TENANT, POLICY_ID, COMMAND, null, null)).isSameAs(mutation);
        verify(client).adjustBillingPlan(TENANT, POLICY_ID, COMMAND, null, null);
    }

    @Test
    void rejectsAnInvalidIfMatchBeforeCallingTheExpert() {
        assertThatThrownBy(() -> service.adjust(TENANT, POLICY_ID, COMMAND, "not-a-version",
                "adjust-key-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_IF_MATCH");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("If-Match"));
                });
        verifyNoInteractions(client);
    }

    @Test
    void rejectsAnOverlongIdempotencyKeyBeforeCallingTheExpert() {
        String key = "x".repeat(IdempotencyKeys.MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.adjust(TENANT, POLICY_ID, COMMAND, null, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("Idempotency-Key"));
                });
        verifyNoInteractions(client);
    }

    private static BillingAdjustmentResult result() {
        BillingPlan plan = new BillingPlan(PLAN_ID, "BP-2026-0001", POLICY_ID,
                new BigDecimal("150000.0000"), "USD", 4, "QUARTERLY",
                LocalDate.of(2026, 1, 15), "ACTIVE", List.of());
        Installment installment = new Installment(INSTALLMENT_ID, 4,
                LocalDate.of(2026, 5, 1), new BigDecimal("30000.0000"),
                new BigDecimal("0.0000"), new BigDecimal("30000.0000"), "DUE", null, null);
        return new BillingAdjustmentResult(plan, installment, new BigDecimal("30000.0000"),
                new BigDecimal("150000.0000"));
    }
}

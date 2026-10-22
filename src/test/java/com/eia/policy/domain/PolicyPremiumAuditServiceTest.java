package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyPremiumAuditServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID AUDIT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPROVED_BY = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID APPROVAL_REF = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final ApplyPremiumAuditCommand COMMAND = new ApplyPremiumAuditCommand(
            new BigDecimal("12500.5000"), LocalDate.of(2026, 11, 1),
            "Annual exposure audit", APPROVED_BY, APPROVAL_REF);

    private final PolicyClient client = mock(PolicyClient.class);
    private final PolicyPremiumAuditService service = new PolicyPremiumAuditService(client);

    @Test
    void delegatesTheAuditAndNormalizesTheIdempotencyKey() {
        Mutation<PremiumAuditResult> mutation =
                new Mutation<>(new PremiumAuditResult(policy(), audit()), "\"5\"", false);
        when(client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1"))
                .thenReturn(mutation);

        assertThat(service.apply(TENANT, POLICY_ID, COMMAND, "\"4\"", " audit-key-1 "))
                .isSameAs(mutation);
        verify(client).applyPremiumAudit(TENANT, POLICY_ID, COMMAND, "\"4\"", "audit-key-1");
    }

    @Test
    void forwardsAbsentConcurrencyAndIdempotencyHeaders() {
        Mutation<PremiumAuditResult> mutation =
                new Mutation<>(new PremiumAuditResult(policy(), null), null, false);
        when(client.applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(mutation);

        assertThat(service.apply(TENANT, POLICY_ID, COMMAND, null, null)).isSameAs(mutation);
        verify(client).applyPremiumAudit(TENANT, POLICY_ID, COMMAND, null, null);
    }

    @Test
    void rejectsAnInvalidIfMatchBeforeCallingTheExpert() {
        assertThatThrownBy(() -> service.apply(TENANT, POLICY_ID, COMMAND, "not-a-version",
                "audit-key-1"))
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

        assertThatThrownBy(() -> service.apply(TENANT, POLICY_ID, COMMAND, null, key))
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

    private static PolicyDto policy() {
        return new PolicyDto(POLICY_ID, TENANT, "GL-2026-0041827", "GL",
                UUID.fromString("22222222-2222-7222-8222-222222222222"), "IN_FORCE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                new BigDecimal("12500.5000"), "USD", new BigDecimal("1000000.0000"), "USD",
                5, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-10-22T09:00:00Z"), null, null, null);
    }

    private static PremiumAuditDto audit() {
        return new PremiumAuditDto(AUDIT_ID, POLICY_ID, new BigDecimal("12500.5000"),
                new BigDecimal("12000.0000"), new BigDecimal("500.5000"), "USD",
                LocalDate.of(2026, 11, 1), "Annual exposure audit", APPROVED_BY, APPROVAL_REF,
                "APPLIED", Instant.parse("2026-10-22T09:00:00Z"));
    }
}

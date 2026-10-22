package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubmissionReferralServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final ReferSubmissionCommand REFER = new ReferSubmissionCommand(
            "Aggregate limit above authority", "Chief Underwriting Officer",
            "Senior UW - Casualty", "Needs reinsurance sign-off");
    private static final DecideReferralCommand DECIDE = new DecideReferralCommand(
            "APPROVED", "Chief Underwriter", "Approved within authority");

    private final PolicyClient client = mock(PolicyClient.class);
    private final SubmissionService service = new SubmissionService(client);

    @Test
    void refersAndNormalizesTheIdempotencyKey() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), "\"1\"", false);
        when(client.referSubmission(TENANT, SUBMISSION_ID, REFER, "\"0\"", "referral-key"))
                .thenReturn(mutation);

        assertThat(service.refer(TENANT, SUBMISSION_ID, REFER, "\"0\"", " referral-key "))
                .isSameAs(mutation);
        verify(client).referSubmission(TENANT, SUBMISSION_ID, REFER, "\"0\"",
                "referral-key");
    }

    @Test
    void refersWithoutConcurrencyOrIdempotencyScopes() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), null, false);
        when(client.referSubmission(TENANT, SUBMISSION_ID, REFER, null, null))
                .thenReturn(mutation);

        assertThat(service.refer(TENANT, SUBMISSION_ID, REFER)).isSameAs(mutation);
        verify(client).referSubmission(TENANT, SUBMISSION_ID, REFER, null, null);
    }

    @Test
    void decidesAndNormalizesTheIdempotencyKey() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), "\"2\"", false);
        when(client.decideSubmissionReferral(TENANT, SUBMISSION_ID, DECIDE, "\"1\"",
                "decision-key")).thenReturn(mutation);

        assertThat(service.decideReferral(TENANT, SUBMISSION_ID, DECIDE, "\"1\"",
                " decision-key ")).isSameAs(mutation);
        verify(client).decideSubmissionReferral(TENANT, SUBMISSION_ID, DECIDE, "\"1\"",
                "decision-key");
    }

    @Test
    void decidesWithoutConcurrencyOrIdempotencyScopes() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), null, false);
        when(client.decideSubmissionReferral(TENANT, SUBMISSION_ID, DECIDE, null, null))
                .thenReturn(mutation);

        assertThat(service.decideReferral(TENANT, SUBMISSION_ID, DECIDE)).isSameAs(mutation);
        verify(client).decideSubmissionReferral(TENANT, SUBMISSION_ID, DECIDE, null, null);
    }

    @Test
    void rejectsAnOverlongReferralIdempotencyKeyBeforeCallingTheExpert() {
        String key = "x".repeat(IdempotencyKeys.MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.refer(TENANT, SUBMISSION_ID, REFER, null, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
                });
        verifyNoInteractions(client);
    }

    @Test
    void rejectsAnOverlongDecisionIdempotencyKeyBeforeCallingTheExpert() {
        String key = "x".repeat(IdempotencyKeys.MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.decideReferral(TENANT, SUBMISSION_ID, DECIDE, null, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("INVALID_IDEMPOTENCY_KEY"));
        verifyNoInteractions(client);
    }

    private static SubmissionDto submission() {
        return new SubmissionDto(SUBMISSION_ID, "SUB-2026-0000001", TENANT, APPLICANT_ID,
                "CGL_PRODUCTS", LocalDate.of(2026, 1, 1),
                new BigDecimal("1000000.0000"), "USD", List.of("ASBESTOS", "PFAS"),
                "broker intake", "REFERRED", 1,
                Instant.parse("2026-10-22T09:00:00Z"),
                Instant.parse("2026-10-22T10:00:00Z"), null,
                "Aggregate limit above authority", "Chief Underwriting Officer",
                "Senior UW - Casualty", "Needs reinsurance sign-off", "PENDING", null, null);
    }
}

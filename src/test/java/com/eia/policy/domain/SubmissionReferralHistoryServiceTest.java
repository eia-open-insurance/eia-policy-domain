package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionReferralHistoryServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID REFERRAL_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");

    private final PolicyClient client = mock(PolicyClient.class);
    private final SubmissionService service = new SubmissionService(client);

    @Test
    void listsTheReferralHistoryFromTheExpert() {
        List<ReferralDto> history = List.of(referral("APPROVED"));
        when(client.findSubmissionReferrals(TENANT, SUBMISSION_ID)).thenReturn(history);

        assertThat(service.listReferrals(TENANT, SUBMISSION_ID)).isSameAs(history);
        verify(client).findSubmissionReferrals(TENANT, SUBMISSION_ID);
    }

    @Test
    void returnsTheEmptyHistoryTheExpertReports() {
        when(client.findSubmissionReferrals(TENANT, SUBMISSION_ID)).thenReturn(List.of());

        assertThat(service.listReferrals(TENANT, SUBMISSION_ID)).isEmpty();
        verify(client).findSubmissionReferrals(TENANT, SUBMISSION_ID);
    }

    @Test
    void propagatesTheExpertNotFoundProblem() {
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                "Underwriting submission not found",
                List.of(), Map.of("submissionId", SUBMISSION_ID.toString()));
        when(client.findSubmissionReferrals(TENANT, SUBMISSION_ID)).thenThrow(notFound);

        assertThatThrownBy(() -> service.listReferrals(TENANT, SUBMISSION_ID))
                .isSameAs(notFound);
        verify(client).findSubmissionReferrals(TENANT, SUBMISSION_ID);
    }

    private static ReferralDto referral(String decision) {
        Instant decidedAt = "PENDING".equals(decision) ? null : Instant.parse("2026-10-22T12:00:00Z");
        return new ReferralDto(REFERRAL_ID, SUBMISSION_ID, "LIMIT",
                "Chief Underwriting Officer", "Senior UW - Casualty",
                "Aggregate limit above authority", decision,
                decidedAt == null ? null : "Chief Underwriter", decidedAt,
                Instant.parse("2026-10-22T09:00:00Z"));
    }
}

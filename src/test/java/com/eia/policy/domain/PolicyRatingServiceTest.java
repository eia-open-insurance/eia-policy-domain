package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyRatingServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID WORKSHEET_ID = UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final UUID CREATED_BY = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final RatePolicyCommand COMMAND = new RatePolicyCommand("RATE_TIMES_EXPOSURE",
            new BigDecimal("0.0125"), new BigDecimal("250000.0000"),
            new BigDecimal("1.250000"), true);

    private final PolicyClient client = mock(PolicyClient.class);
    private final PolicyRatingService service = new PolicyRatingService(client);

    @Test
    void delegatesTheRatingAndNormalizesTheIdempotencyKey() {
        Mutation<PolicyRatingResult> mutation = new Mutation<>(
                new PolicyRatingResult(worksheet(), policy()), "\"5\"", false);
        when(client.ratePolicy(TENANT, POLICY_ID, COMMAND, "\"4\"", "rating-key-1"))
                .thenReturn(mutation);

        assertThat(service.rate(TENANT, POLICY_ID, COMMAND, "\"4\"", " rating-key-1 "))
                .isSameAs(mutation);
        verify(client).ratePolicy(TENANT, POLICY_ID, COMMAND, "\"4\"", "rating-key-1");
    }

    @Test
    void forwardsAbsentConcurrencyAndIdempotencyHeaders() {
        Mutation<PolicyRatingResult> mutation = new Mutation<>(
                new PolicyRatingResult(worksheet(), null), null, false);
        when(client.ratePolicy(TENANT, POLICY_ID, COMMAND, null, null)).thenReturn(mutation);

        assertThat(service.rate(TENANT, POLICY_ID, COMMAND, null, null)).isSameAs(mutation);
        verify(client).ratePolicy(TENANT, POLICY_ID, COMMAND, null, null);
    }

    @Test
    void delegatesTheLatestWorksheet() {
        RatingWorksheetDto worksheet = worksheet();
        when(client.findLatestRating(TENANT, POLICY_ID)).thenReturn(worksheet);

        assertThat(service.latest(TENANT, POLICY_ID)).isSameAs(worksheet);
        verify(client).findLatestRating(TENANT, POLICY_ID);
    }

    @Test
    void rejectsAnInvalidIfMatchBeforeCallingTheExpert() {
        assertThatThrownBy(() -> service.rate(TENANT, POLICY_ID, COMMAND, "not-a-version",
                "rating-key-1"))
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

        assertThatThrownBy(() -> service.rate(TENANT, POLICY_ID, COMMAND, null, key))
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

    private static RatingWorksheetDto worksheet() {
        return new RatingWorksheetDto(WORKSHEET_ID, TENANT, POLICY_ID,
                "RATE_TIMES_EXPOSURE", new BigDecimal("0.012500"),
                new BigDecimal("250000.0000"), new BigDecimal("1.250000"),
                new BigDecimal("3906.2500"), "USD", "a".repeat(64), "CALCULATED",
                Instant.parse("2026-10-22T09:00:00Z"), CREATED_BY);
    }

    private static PolicyDto policy() {
        return new PolicyDto(POLICY_ID, TENANT, "GL-2026-0041827", "GL",
                UUID.fromString("22222222-2222-7222-8222-222222222222"), "DRAFT",
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2027, 1, 1),
                new BigDecimal("3906.2500"), "USD", new BigDecimal("1000000.0000"), "USD",
                5, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-10-22T09:00:00Z"), null, null, null);
    }
}

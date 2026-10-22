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

class SubmissionServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final CreateSubmissionCommand COMMAND = new CreateSubmissionCommand(
            APPLICANT_ID, "CGL_PRODUCTS", LocalDate.of(2026, 1, 1),
            new BigDecimal("1000000.0000"), "USD", List.of("ASBESTOS", "PFAS"),
            "broker intake");
    private static final UpdateSubmissionStatusCommand STATUS =
            new UpdateSubmissionStatusCommand("QUOTED", "ready to quote");
    private static final ConvertSubmissionCommand CONVERT =
            new ConvertSubmissionCommand(new BigDecimal("12500.0000"), "USD",
                    LocalDate.of(2027, 1, 1));

    private final PolicyClient client = mock(PolicyClient.class);
    private final SubmissionService service = new SubmissionService(client);

    @Test
    void delegatesTheList() {
        List<SubmissionDto> submissions = List.of(submission());
        when(client.findSubmissions(TENANT)).thenReturn(submissions);

        assertThat(service.list(TENANT)).isSameAs(submissions);
        verify(client).findSubmissions(TENANT);
    }

    @Test
    void delegatesTheRead() {
        SubmissionDto submission = submission();
        when(client.findSubmission(TENANT, SUBMISSION_ID)).thenReturn(submission);

        assertThat(service.get(TENANT, SUBMISSION_ID)).isSameAs(submission);
        verify(client).findSubmission(TENANT, SUBMISSION_ID);
    }

    @Test
    void createsAndNormalizesTheIdempotencyKey() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), "\"2\"", false);
        when(client.createSubmission(TENANT, COMMAND, "sub-key")).thenReturn(mutation);

        assertThat(service.create(TENANT, COMMAND, " sub-key ")).isSameAs(mutation);
        verify(client).createSubmission(TENANT, COMMAND, "sub-key");
    }

    @Test
    void createsWithoutAnIdempotencyKeyWhenNoneIsSupplied() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), null, false);
        when(client.createSubmission(TENANT, COMMAND, null)).thenReturn(mutation);

        assertThat(service.create(TENANT, COMMAND, null)).isSameAs(mutation);
        verify(client).createSubmission(TENANT, COMMAND, null);
    }

    @Test
    void updatesTheStatusAndForwardsTheConcurrencyAndIdempotencyScopes() {
        Mutation<SubmissionDto> mutation = new Mutation<>(submission(), "\"3\"", false);
        when(client.updateSubmissionStatus(TENANT, SUBMISSION_ID, STATUS, "\"2\"", "status-key"))
                .thenReturn(mutation);

        assertThat(service.updateStatus(TENANT, SUBMISSION_ID, STATUS, "\"2\"", " status-key "))
                .isSameAs(mutation);
        verify(client).updateSubmissionStatus(TENANT, SUBMISSION_ID, STATUS, "\"2\"",
                "status-key");
    }

    @Test
    void convertsAndNormalizesTheIdempotencyKey() {
        Mutation<SubmissionConversionResult> mutation = new Mutation<>(
                new SubmissionConversionResult(submission(), null), "\"0\"", false);
        when(client.convertSubmission(TENANT, SUBMISSION_ID, CONVERT, "convert-key"))
                .thenReturn(mutation);

        assertThat(service.convert(TENANT, SUBMISSION_ID, CONVERT, " convert-key "))
                .isSameAs(mutation);
        verify(client).convertSubmission(TENANT, SUBMISSION_ID, CONVERT, "convert-key");
    }

    @Test
    void createsTheConversionWithoutAnIdempotencyKeyWhenNoneIsSupplied() {
        Mutation<SubmissionConversionResult> mutation = new Mutation<>(
                new SubmissionConversionResult(submission(), null), null, false);
        when(client.convertSubmission(TENANT, SUBMISSION_ID, CONVERT, null))
                .thenReturn(mutation);

        assertThat(service.convert(TENANT, SUBMISSION_ID, CONVERT, null)).isSameAs(mutation);
        verify(client).convertSubmission(TENANT, SUBMISSION_ID, CONVERT, null);
    }

    @Test
    void rejectsAnOverlongIdempotencyKeyBeforeCallingTheExpert() {
        String key = "x".repeat(IdempotencyKeys.MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.create(TENANT, COMMAND, key))
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

    @Test
    void rejectsAnOverlongConversionIdempotencyKeyBeforeCallingTheExpert() {
        String key = "x".repeat(IdempotencyKeys.MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.convert(TENANT, SUBMISSION_ID, CONVERT, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
                });
        verifyNoInteractions(client);
    }

    private static SubmissionDto submission() {
        return new SubmissionDto(SUBMISSION_ID, "SUB-2026-0000001", TENANT, APPLICANT_ID,
                "CGL_PRODUCTS", LocalDate.of(2026, 1, 1),
                new BigDecimal("1000000.0000"), "USD", List.of("ASBESTOS", "PFAS"),
                "broker intake", "RECEIVED", 2,
                Instant.parse("2026-10-22T09:00:00Z"),
                Instant.parse("2026-10-22T10:00:00Z"));
    }
}

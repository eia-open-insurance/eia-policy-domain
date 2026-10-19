package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyContractServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID LIMIT_ID = UUID.fromString("88888888-8888-7888-8888-888888888888");

    @Mock
    private PolicyClient client;

    @Test
    void rebuildsPublicPageLinksForVersions() {
        when(client.findVersions(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ExpertItemPage<>(List.of(version()), 0, 20, 41));

        ItemPage<PolicyVersionDto> page = service().listVersions(TENANT, POLICY_ID, 0, 20);

        assertThat(page.total()).isEqualTo(41);
        assertThat(page._links().self().href())
                .isEqualTo("/api/v1/policies/" + POLICY_ID + "/versions?page=0&size=20");
        assertThat(page._links().next().href())
                .isEqualTo("/api/v1/policies/" + POLICY_ID + "/versions?page=1&size=20");
        assertThat(page._links().prev()).isNull();
    }

    @Test
    void rejectsInvalidPaginationBeforeCallingTheExpert() {
        assertThatThrownBy(() -> service().listCoverages(TENANT, POLICY_ID, 0, 101))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("VALIDATION_ERROR"));
        verify(client, never()).findCoverages(any(), any(), anyInt(), anyInt());
    }

    @Test
    void rejectsAMalformedIfMatch() {
        assertThatThrownBy(() -> service().createCoverage(TENANT, POLICY_ID,
                coverageCommand(), "not-a-version", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_IF_MATCH");
                });
        verify(client, never()).createCoverage(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnOverlongIdempotencyKey() {
        String key = "k".repeat(256);

        assertThatThrownBy(() -> service().createCoverage(TENANT, POLICY_ID,
                coverageCommand(), null, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo("INVALID_IDEMPOTENCY_KEY"));
        verify(client, never()).createCoverage(any(), any(), any(), any(), any());
    }

    @Test
    void normalizesAndForwardsTheIdempotencyKey() {
        Mutation<CoverageViewDto> mutation = new Mutation<>(null, "\"5\"", false);
        when(client.createCoverage(TENANT, POLICY_ID, coverageCommand(), "\"4\"", "key-1"))
                .thenReturn(mutation);

        Mutation<CoverageViewDto> result = service().createCoverage(TENANT, POLICY_ID,
                coverageCommand(), "\"4\"", "  key-1  ");

        assertThat(result).isSameAs(mutation);
    }

    @Test
    void delegatesCancellationAndKeepsTheExpertMutation() {
        CancelPolicyCommand command = new CancelPolicyCommand("PRO_RATA", "Insured request",
                LocalDate.of(2026, 7, 2), 0, null, null);
        Mutation<CancellationDto> mutation = new Mutation<>(null, "\"5\"", true);
        when(client.cancelPolicy(TENANT, POLICY_ID, command, "\"4\"", "key-1"))
                .thenReturn(mutation);

        Mutation<CancellationDto> result = service().cancelPolicy(TENANT, POLICY_ID, command,
                "\"4\"", "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.etag()).isEqualTo("\"5\"");
    }

    @Test
    void delegatesErosionFacts() {
        ErosionCommand command = new ErosionCommand(LIMIT_ID, UUID.randomUUID(),
                UUID.randomUUID(), 1L, new BigDecimal("250000"), new BigDecimal("50000"),
                BigDecimal.ZERO, Instant.parse("2026-10-19T12:00:00Z"));
        Mutation<LimitStateDto> mutation = new Mutation<>(null, "\"6\"", false);
        when(client.applyErosion(TENANT, POLICY_ID, command, null)).thenReturn(mutation);

        assertThat(service().applyErosion(TENANT, POLICY_ID, command, null)).isSameAs(mutation);
    }

    private PolicyContractService service() {
        return new PolicyContractService(client);
    }

    private static CreateCoverageCommand coverageCommand() {
        return new CreateCoverageCommand("GL_PRODUCTS", "Products liability", "A", "OCCURRENCE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("0.10"),
                new CreateCoverageCommand.LimitCommand(new BigDecimal("2000000"),
                        new BigDecimal("5000000"), null, new BigDecimal("1000000"),
                        new BigDecimal("5000"), new BigDecimal("0.10"), "USD", null));
    }

    private static PolicyVersionDto version() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new PolicyVersionDto(UUID.randomUUID(), TENANT, POLICY_ID, 3,
                "POL-2026-0000001-03", "ENDORSEMENT", "ISSUED", LocalDate.of(2026, 6, 1), null,
                now, null, null, null, new BigDecimal("5000.0000"), "USD", "Endorsement", null,
                "hash", now);
    }
}

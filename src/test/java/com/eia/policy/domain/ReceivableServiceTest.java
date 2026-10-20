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

class ReceivableServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");

    private final PolicyClient client = mock(PolicyClient.class);
    private final ReceivableService service = new ReceivableService(client);

    @Test
    void normalizesTheStatusAndDefaultsTheLimit() {
        ReceivableSummary summary = new ReceivableSummary(List.of(), BigDecimal.ZERO,
                BigDecimal.ZERO, 0);
        when(client.findReceivables(TENANT, "OVERDUE", LocalDate.of(2026, 2, 1), 20))
                .thenReturn(summary);

        assertThat(service.list(TENANT, " overdue ", LocalDate.of(2026, 2, 1), null))
                .isSameAs(summary);
        verify(client).findReceivables(TENANT, "OVERDUE", LocalDate.of(2026, 2, 1), 20);
    }

    @Test
    void treatsABlankStatusAsAbsent() {
        when(client.findReceivables(TENANT, null, null, 5))
                .thenReturn(new ReceivableSummary(List.of(), null, null, 0));

        service.list(TENANT, "   ", null, 5);
        verify(client).findReceivables(TENANT, null, null, 5);
    }

    @Test
    void acceptsTheMaximumLimit() {
        when(client.findReceivables(TENANT, null, null, 200))
                .thenReturn(new ReceivableSummary(List.of(), null, null, 0));

        service.list(TENANT, null, null, 200);
        verify(client).findReceivables(TENANT, null, null, 200);
    }

    @Test
    void rejectsLimitsOutsideOneToTwoHundred() {
        for (Integer limit : new Integer[]{0, -1, 201}) {
            assertThatThrownBy(() -> service.list(TENANT, null, null, limit))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException api = (ApiException) ex;
                        assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(api.code()).isEqualTo("VALIDATION_ERROR");
                        assertThat(api.details()).singleElement().satisfies(issue -> {
                            assertThat(issue.field()).isEqualTo("limit");
                            assertThat(issue.code()).isEqualTo("OUT_OF_RANGE");
                        });
                    });
        }
        verifyNoInteractions(client);
    }
}

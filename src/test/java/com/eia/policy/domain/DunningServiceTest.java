package com.eia.policy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DunningServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID INSTALLMENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");

    private final PolicyClient client = mock(PolicyClient.class);
    private final DunningService service = new DunningService(client);

    @Test
    void delegatesTheHistoryReadToTheExpertClient() {
        List<DunningAction> history = List.of(action());
        when(client.findDunningActions(TENANT, INSTALLMENT_ID)).thenReturn(history);

        assertThat(service.list(TENANT, INSTALLMENT_ID)).isSameAs(history);
        verify(client).findDunningActions(TENANT, INSTALLMENT_ID);
    }

    @Test
    void delegatesTheRecordToTheExpertClient() {
        CreateDunningActionCommand command = new CreateDunningActionCommand("REMINDER", "EMAIL",
                null);
        DunningAction action = action();
        when(client.recordDunningAction(TENANT, INSTALLMENT_ID, command)).thenReturn(action);

        assertThat(service.record(TENANT, INSTALLMENT_ID, command)).isSameAs(action);
        verify(client).recordDunningAction(TENANT, INSTALLMENT_ID, command);
    }

    private static DunningAction action() {
        return new DunningAction(
                UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc"), INSTALLMENT_ID,
                UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa"),
                UUID.fromString("33333333-3333-7333-8333-333333333333"),
                "REMINDER", "EMAIL", null, "SENT",
                Instant.parse("2026-06-15T12:00:00Z"));
    }
}

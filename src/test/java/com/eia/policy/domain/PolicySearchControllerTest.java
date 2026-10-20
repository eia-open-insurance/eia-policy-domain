package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicySearchController.class)
@Import(PolicySearchService.class)
class PolicySearchControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final String URL = "/api/v1/policies/search";
    private static final String DIRECT = "DIRECT";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicySearchRepository repository;

    @Test
    void returnsTheCommonSearchEnvelopeAndHref() throws Exception {
        when(repository.search(TENANT, "cgl", 10)).thenReturn(List.of(
                new PolicySearchRow(POLICY_ID, "POL-2026-0000001", "CGL", "DRAFT")));

        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .header("X-EIA-Book", DIRECT)
                        .param("q", "cgl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("cgl"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.items[0].id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.items[0].type").value("POLICY"))
                .andExpect(jsonPath("$.items[0].label").value("POL-2026-0000001"))
                .andExpect(jsonPath("$.items[0].secondary").value("CGL \u00B7 DRAFT"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].href").value("/policy?record=" + POLICY_ID));
    }

    @Test
    void blankQueryReturnsTheLatestTenByDefault() throws Exception {
        List<PolicySearchRow> rows = IntStream.range(0, 10)
                .mapToObj(i -> new PolicySearchRow(
                        UUID.fromString(String.format("00000000-0000-7000-8000-%012d", i + 1)),
                        String.format("POL-2026-%07d", i + 1), "CGL", "DRAFT"))
                .toList();
        when(repository.search(TENANT, "", 10)).thenReturn(rows);

        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(""))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.items.length()").value(10));
    }

    @Test
    void limitsAboveTheMaximumAreCappedAtTwentyFive() throws Exception {
        when(repository.search(TENANT, "cgl", 25)).thenReturn(List.of());

        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .header("X-EIA-Book", DIRECT)
                        .param("q", "cgl")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(25));

        verify(repository).search(TENANT, "cgl", 25);
    }

    @Test
    void rejectsALimitBelowOne() throws Exception {
        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .header("X-EIA-Book", DIRECT)
                        .param("q", "cgl")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));

        verifyNoInteractions(repository);
    }

    @Test
    void requiresTheDirectBookHeader() throws Exception {
        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .param("q", "cgl"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_REQUIRED"))
                .andExpect(jsonPath("$.category").value("VALIDATION"));

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnyOtherBookBeforeQuerying() throws Exception {
        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .header("X-EIA-Book", "ASSUMED")
                        .param("q", "cgl"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(repository);
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(repository.search(TENANT, "cgl", 10)).thenReturn(List.of());

        mvc.perform(get(URL)
                        .header("X-EIA-Tenant", TENANT)
                        .header("X-EIA-Book", "direct")
                        .param("q", "cgl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("cgl"));
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(URL)
                        .header("X-EIA-Book", DIRECT)
                        .param("q", "cgl"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"));

        verifyNoInteractions(repository);
    }
}

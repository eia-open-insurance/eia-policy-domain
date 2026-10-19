package com.eia.policy.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageLinksTest {
    @Test
    void singlePageHasSelfOnly() {
        PolicyPage.Links links = PageLinks.build("/api/v1/policies", Map.of(), 0, 20, 20);
        assertThat(links.self().href()).isEqualTo("/api/v1/policies?page=0&size=20");
        assertThat(links.next()).isNull();
        assertThat(links.prev()).isNull();
    }

    @Test
    void firstPageOfManyHasNextOnly() {
        PolicyPage.Links links = PageLinks.build("/api/v1/policies", Map.of(), 0, 20, 21);
        assertThat(links.next().href()).isEqualTo("/api/v1/policies?page=1&size=20");
        assertThat(links.prev()).isNull();
    }

    @Test
    void interiorPageHasBothLinks() {
        PolicyPage.Links links = PageLinks.build("/api/v1/policies", Map.of(), 1, 20, 41);
        assertThat(links.next().href()).isEqualTo("/api/v1/policies?page=2&size=20");
        assertThat(links.prev().href()).isEqualTo("/api/v1/policies?page=0&size=20");
    }

    @Test
    void lastPageHasPreviousOnly() {
        PolicyPage.Links links = PageLinks.build("/api/v1/policies", Map.of(), 2, 20, 41);
        assertThat(links.next()).isNull();
        assertThat(links.prev().href()).isEqualTo("/api/v1/policies?page=1&size=20");
    }

    @Test
    void emptyTenantHasNoBoundaryLinks() {
        PolicyPage.Links links = PageLinks.build("/api/v1/policies", Map.of(), 0, 20, 0);
        assertThat(links.self()).isNotNull();
        assertThat(links.next()).isNull();
        assertThat(links.prev()).isNull();
    }

    @Test
    void pageBeyondTheDataStillHasNoPreviousWhenNoPageExists() {
        PolicyPage.Links links = PageLinks.build("/api/v1/policies", Map.of(), 5, 20, 21);
        assertThat(links.next()).isNull();
        assertThat(links.prev()).isNull();
    }
}
